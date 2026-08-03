package com.talentledger.application.service;

import com.talentledger.application.dto.response.ContactResponse;
import com.talentledger.application.port.inbound.ContactUseCase;
import com.talentledger.application.port.inbound.ContactUseCase.BulkOperationCommand;
import com.talentledger.application.port.inbound.ContactUseCase.BulkOperationResult;
import com.talentledger.application.port.inbound.ContactUseCase.ContactSearchResult;
import com.talentledger.application.port.inbound.ContactUseCase.CreateContactCommand;
import com.talentledger.application.port.inbound.ContactUseCase.CursorPage;
import com.talentledger.application.port.inbound.ContactUseCase.EnrichContactCommand;
import com.talentledger.application.port.inbound.ContactUseCase.EnrichmentResult;
import com.talentledger.application.port.inbound.ContactUseCase.UpdateContactCommand;
import com.talentledger.application.port.outbound.AiClientPort;
import com.talentledger.domain.contact.Contact;
import com.talentledger.domain.contact.ContactCommandRepository;
import com.talentledger.domain.contact.ContactRepository;
import com.talentledger.domain.shared.Result;
import com.talentledger.domain.user.UserPlan;
import com.talentledger.domain.user.UserQuotaRepository;
import com.talentledger.domain.user.UserRepository;
import com.talentledger.infrastructure.persistence.entity.AiEnrichmentEntity;
import com.talentledger.infrastructure.persistence.entity.CompanyEntity;
import com.talentledger.infrastructure.persistence.entity.ContactEntity;
import com.talentledger.infrastructure.persistence.repository.CompanyJpaRepository;
import com.talentledger.infrastructure.persistence.repository.ContactJpaRepository;
import com.talentledger.infrastructure.persistence.repository.JpaAiEnrichmentRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService implements ContactUseCase {

    private final ContactRepository contactRepository;
    private final ContactCommandRepository contactCommandRepository;
    private final ContactJpaRepository contactJpaRepository;
    private final UserRepository userRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final AiClientPort aiClientPort;
    private final JpaAiEnrichmentRepository aiEnrichmentRepository;
    private final CompanyJpaRepository companyJpaRepository;

    /**
     * Per the tier model (Master Architecture §3): FREE plan is read-only —
     * users can browse their demo dump but not create/edit/delete contacts.
     * This was never enforced anywhere before; create/update/delete/bulk all
     * silently allowed FREE users full CRUD. Returns a failure Result if the
     * user's plan forbids writes, or empty if the write may proceed.
     */
    private Optional<Result<ContactResponse, String>> requireWriteAccess(UUID userId) {
        UserPlan plan = userRepository.findById(userId).map(u -> u.getPlan()).orElse(UserPlan.FREE);
        if (plan == UserPlan.FREE) {
            return Optional.of(Result.failure("Your plan is read-only. Upgrade to Pro to create, edit, or delete contacts."));
        }
        return Optional.empty();
    }

    @Override
    public Result<ContactResponse, String> getContact(UUID contactId, UUID userId) {
        return contactRepository.findByIdAndUserId(contactId, userId)
                .map(c -> Result.<ContactResponse, String>success(toContactResponse(c)))
                .orElseGet(() -> Result.failure("Contact not found"));
    }

    @Override
    public Result<ContactSearchResult, String> searchContacts(UUID userId, ContactSearchQuery query) {
        int size = Math.min(Math.max(query.pageSize(), 1), 100);

        // Cursor here is simply an encoded page number ("p:N"), not a true
        // keyset cursor — a deliberate, documented tradeoff. True composite
        // (sortField, id) keyset pagination doesn't compose cleanly with an
        // arbitrary, user-chosen sort field + free-text filtering without a
        // much larger rewrite; offset paging is correct and simple here, and
        // can be revisited if/when result sets get large enough that OFFSET
        // cost matters (thousands of pages deep).
        int pageNumber = 0;
        if (query.nextCursor() != null && query.nextCursor().startsWith("p:")) {
            try {
                pageNumber = Math.max(0, Integer.parseInt(query.nextCursor().substring(2)));
            } catch (NumberFormatException ignored) { /* fall back to page 0 */ }
        }

        Specification<ContactEntity> spec = buildSearchSpecification(userId, query);
        Sort sort = parseSort(query.sortBy());
        var pageable = PageRequest.of(pageNumber, size, sort);

        var resultPage = contactJpaRepository.findAll(spec, pageable);

        List<ContactResponse> contacts = resultPage.getContent().stream()
                .map(this::entityToResponse)
                .toList();

        boolean hasMore = resultPage.hasNext();
        String nextCursor = hasMore ? "p:" + (pageNumber + 1) : null;

        return Result.success(new CursorPage(contacts, nextCursor, hasMore));
    }

    /**
     * Builds the dynamic WHERE clause for contact search: always scoped to
     * the owning user and non-deleted rows, plus a free-text match across
     * name/email/title/domain (when {@code query.query()} is present) and
     * any exact-match {@link FilterCondition}s the controller attached
     * (seniority, location, domain, company). "company" is resolved by name
     * against the companies table first since {@link ContactEntity} only
     * stores a {@code companyId}, not a display name.
     */
    private Specification<ContactEntity> buildSearchSpecification(UUID userId, ContactSearchQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.isNull(root.get("deletedAt")));

            String text = query.query();
            if (text != null && !text.isBlank()) {
                String like = "%" + text.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("domain"), "")), like)
                ));
            }

            if (query.filters() != null) {
                for (FilterCondition f : query.filters()) {
                    if (f.value() == null) continue;
                    String value = f.value().toString();
                    if (value.isBlank()) continue;

                    switch (f.field()) {
                        case "seniorityLevel" -> {
                            try {
                                predicates.add(cb.equal(root.get("seniorityLevel"),
                                        com.talentledger.domain.contact.SeniorityLevel.valueOf(value.toUpperCase())));
                            } catch (IllegalArgumentException ignored) { /* unknown level, skip filter */ }
                        }
                        case "location" -> predicates.add(cb.equal(cb.lower(root.get("location")), value.toLowerCase()));
                        case "domain" -> predicates.add(cb.equal(cb.lower(root.get("domain")), value.toLowerCase()));
                        case "company" -> {
                            List<UUID> matchingCompanyIds = companyJpaRepository
                                    .searchCompanies(value, org.springframework.data.domain.PageRequest.of(0, 50))
                                    .stream().map(CompanyEntity::getId).toList();
                            // No matching company: force an empty result rather
                            // than silently ignoring the filter.
                            predicates.add(matchingCompanyIds.isEmpty()
                                    ? cb.disjunction()
                                    : root.get("companyId").in(matchingCompanyIds));
                        }
                        default -> { /* unrecognized filter field, ignore rather than error */ }
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Parses a composite sort string like {@code "name_asc"} or
     * {@code "created_desc"} into a Spring {@link Sort}. Falls back to
     * newest-first when the value is missing or unrecognized, matching the
     * previous default behavior so existing callers that don't care about
     * sort see no change.
     */
    private Sort parseSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        }
        int lastUnderscore = sortBy.lastIndexOf('_');
        String field = lastUnderscore > 0 ? sortBy.substring(0, lastUnderscore) : sortBy;
        String dir = lastUnderscore > 0 ? sortBy.substring(lastUnderscore + 1) : "asc";
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        String entityField = switch (field) {
            case "name" -> "name";
            case "company" -> "companyId"; // company *name* isn't on this entity; id groups the same way
            case "created", "date" -> "createdAt";
            case "verification" -> "verificationScore";
            default -> "createdAt";
        };
        // Always add id as a tiebreaker so equal sort values still paginate deterministically.
        return Sort.by(direction, entityField).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Override
    public Result<ContactResponse, String> createContact(UUID userId, CreateContactCommand command) {
        var gate = requireWriteAccess(userId);
        if (gate.isPresent()) return gate.get();

        var quota = userQuotaRepository.findByUserId(userId).orElse(null);
        if (quota == null) {
            return Result.failure("Could not verify your account quota — please try again.");
        }
        // Check against the LIVE count, not the cached quota.contactsStoredCount counter —
        // that counter is updated incrementally on every create/delete and can drift out
        // of sync (e.g. a delete that failed to reach the decrement, a dump whose contacts
        // were never rolled into it). The live count from the contacts table is always correct.
        long liveContactCount = contactJpaRepository.countByUserIdAndDeletedAtIsNull(userId);
        if (liveContactCount + 1 > quota.getContactsStoredLimit()) {
            return Result.failure("You've reached your contact storage limit. Upgrade to add more.");
        }

        String normalizedEmail = command.email() != null ? command.email().toLowerCase().trim() : null;
        if (normalizedEmail != null && contactRepository.existsByNormalizedEmailAndUserId(normalizedEmail, userId)) {
            return Result.failure("Contact with this email already exists");
        }

        ContactEntity entity = ContactEntity.builder()
                .userId(userId)
                .name(command.name())
                .email(command.email())
                .normalizedEmail(normalizedEmail)
                .phone(command.phone())
                .linkedinUrl(command.linkedinUrl())
                .secondaryEmail(command.secondaryEmail())
                .title(command.title())
                .department(command.department())
                .seniorityLevel(command.seniorityLevel() != null ?
                        com.talentledger.domain.contact.SeniorityLevel.valueOf(command.seniorityLevel()) : null)
                .location(command.location())
                .timezone(command.timezone())
                .language(command.language())
                .notes(command.notes())
                .tags(command.tags() != null ? command.tags() : List.of())
                .primaryDumpId(command.primaryDumpId())
                .companyId(command.companyId())
                .verificationScore(0)
                .source("manual")
                .status(com.talentledger.domain.contact.ContactStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ContactEntity saved = contactJpaRepository.save(entity);
        userQuotaRepository.save(quota.addContacts(1));
        return Result.success(entityToResponse(saved));
    }

    @Override
    public Result<ContactResponse, String> updateContact(UUID contactId, UUID userId, UpdateContactCommand command) {
        var gate = requireWriteAccess(userId);
        if (gate.isPresent()) return gate.get();

        return contactJpaRepository.findByIdAndUserId(contactId, userId)
                .map(entity -> {
                    if (command.name() != null) entity.setName(command.name());
                    if (command.phone() != null) entity.setPhone(command.phone());
                    if (command.linkedinUrl() != null) entity.setLinkedinUrl(command.linkedinUrl());
                    if (command.secondaryEmail() != null) entity.setSecondaryEmail(command.secondaryEmail());
                    if (command.title() != null) entity.setTitle(command.title());
                    if (command.department() != null) entity.setDepartment(command.department());
                    if (command.seniorityLevel() != null)
                        entity.setSeniorityLevel(com.talentledger.domain.contact.SeniorityLevel.valueOf(command.seniorityLevel()));
                    if (command.location() != null) entity.setLocation(command.location());
                    if (command.timezone() != null) entity.setTimezone(command.timezone());
                    if (command.language() != null) entity.setLanguage(command.language());
                    if (command.notes() != null) entity.setNotes(command.notes());
                    if (command.tags() != null) entity.setTags(command.tags());
                    if (command.customFields() != null) entity.setCustomFields(command.customFields());
                    entity.setUpdatedAt(Instant.now());

                    ContactEntity saved = contactJpaRepository.save(entity);
                    return Result.<ContactResponse, String>success(entityToResponse(saved));
                })
                .orElseGet(() -> Result.failure("Contact not found"));
    }

    @Override
    public Result<Void, String> deleteContact(UUID contactId, UUID userId) {
        UserPlan plan = userRepository.findById(userId).map(u -> u.getPlan()).orElse(UserPlan.FREE);
        if (plan == UserPlan.FREE) {
            return Result.failure("Your plan is read-only. Upgrade to Pro to create, edit, or delete contacts.");
        }

        return contactJpaRepository.findByIdAndUserId(contactId, userId)
                .map(entity -> {
                    entity.setDeletedAt(Instant.now());
                    entity.setStatus(com.talentledger.domain.contact.ContactStatus.DELETED);
                    entity.setUpdatedAt(Instant.now());
                    contactJpaRepository.save(entity);
                    userQuotaRepository.findByUserId(userId)
                            .ifPresent(q -> userQuotaRepository.save(q.removeContacts(1)));
                    return Result.<Void, String>success(null);
                })
                .orElseGet(() -> Result.failure("Contact not found"));
    }

    @Override
    public Result<BulkOperationResult, String> bulkOperation(UUID userId, BulkOperationCommand command) {
        UserPlan plan = userRepository.findById(userId).map(u -> u.getPlan()).orElse(UserPlan.FREE);
        if (plan == UserPlan.FREE) {
            return Result.failure("Your plan is read-only. Upgrade to Pro to create, edit, or delete contacts.");
        }

        return switch (command.type()) {
            case DELETE -> {
                int processed = 0;
                for (UUID id : command.contactIds()) {
                    var result = deleteContact(id, userId);
                    if (result.isSuccess()) processed++;
                }
                yield Result.success(new BulkOperationResult(processed, 0, command.contactIds().size() - processed, List.of()));
            }
            case ARCHIVE -> {
                int processed = 0;
                for (UUID id : command.contactIds()) {
                    contactJpaRepository.findByIdAndUserId(id, userId).ifPresent(entity -> {
                        entity.setStatus(com.talentledger.domain.contact.ContactStatus.ARCHIVED);
                        entity.setUpdatedAt(Instant.now());
                        contactJpaRepository.save(entity);
                    });
                    processed++;
                }
                yield Result.success(new BulkOperationResult(processed, 0, 0, List.of()));
            }
            default -> Result.success(new BulkOperationResult(0, 0, 0, List.of("Operation not supported yet")));
        };
    }

    @Override
    public Result<EnrichmentResult, String> enrichContact(UUID contactId, UUID userId, EnrichContactCommand command) {
        UserPlan plan = userRepository.findById(userId).map(u -> u.getPlan()).orElse(UserPlan.FREE);
        if (plan == UserPlan.FREE) {
            return Result.failure("AI enrichment is a Pro/Team feature. Upgrade to use it.");
        }

        var quota = userQuotaRepository.findByUserId(userId).orElse(null);
        if (quota == null || !quota.canUseAi(1)) {
            return Result.failure("Your monthly AI credit limit has been reached.");
        }

        var contactOpt = contactRepository.findByIdAndUserId(contactId, userId);
        if (contactOpt.isEmpty()) {
            return Result.failure("Contact not found");
        }
        Contact contact = contactOpt.get();

        AiEnrichmentEntity.EnrichmentType type;
        try {
            type = AiEnrichmentEntity.EnrichmentType.valueOf(command.type());
        } catch (Exception e) {
            return Result.failure("Unknown enrichment type: " + command.type());
        }

        String content;
        AiClientPort.AiEnrichmentResult aiResult = null;
        if (type == AiEnrichmentEntity.EnrichmentType.EMAIL_DRAFT) {
            content = aiClientPort.generateColdEmail(contact, command.tone());
        } else if (type == AiEnrichmentEntity.EnrichmentType.HIRING_SIGNAL) {
            content = aiClientPort.analyzeHiringSignal(contact);
        } else {
            aiResult = aiClientPort.enrichContact(contact, command.type());
            content = aiResult.generatedContent();
        }

        AiEnrichmentEntity entity = AiEnrichmentEntity.builder()
                .contactId(contactId)
                .userId(userId)
                .enrichmentType(type)
                .modelUsed(aiResult != null ? aiResult.modelUsed() : "claude")
                .promptTokens(aiResult != null ? aiResult.promptTokens() : 0)
                .completionTokens(aiResult != null ? aiResult.completionTokens() : 0)
                .totalTokens(aiResult != null ? aiResult.promptTokens() + aiResult.completionTokens() : 0)
                .generatedContent(content)
                .confidenceScore(java.math.BigDecimal.valueOf(aiResult != null ? aiResult.confidenceScore() : 0.7))
                .createdAt(Instant.now())
                .build();
        AiEnrichmentEntity saved = aiEnrichmentRepository.save(entity);

        userQuotaRepository.save(quota.useAiCredits(1));

        return Result.success(new EnrichmentResult(
                saved.getId(), type.name(), content, saved.getModelUsed(),
                saved.getPromptTokens(), saved.getCompletionTokens(),
                saved.getConfidenceScore() != null ? saved.getConfidenceScore().doubleValue() : 0.7));
    }

    /** Domain Contact → DTO (used when coming from domain repository) */
    private ContactResponse toContactResponse(Contact c) {
        return new ContactResponse(
                c.getId(), c.getName(),
                c.getEmail().getValue(),
                c.getNormalizedEmail() != null ? c.getNormalizedEmail().getValue() : null,
                c.getPhone(), c.getLinkedinUrl(), c.getSecondaryEmail(),
                c.getTitle(), c.getDepartment(),
                c.getSeniorityLevel() != null ? c.getSeniorityLevel().name() : null,
                c.getLocation(), c.getTimezone(), c.getLanguage(),
                c.getDomain(), c.getVerificationScore(), c.getSource(),
                c.getPrimaryDumpId(), c.getCompanyId(), null,
                c.getNotes(), new ArrayList<>(c.getTags()),
                new HashMap<>(c.getCustomFields()),
                new HashMap<>(c.getAiEnrichment()),
                c.getStatus().name(), c.getCreatedAt(), c.getUpdatedAt());
    }

    /** JPA Entity → DTO (used when coming from JPA repository) */
    private ContactResponse entityToResponse(ContactEntity e) {
        return new ContactResponse(
                e.getId(), e.getName(),
                e.getEmail(), e.getNormalizedEmail(),
                e.getPhone(), e.getLinkedinUrl(), e.getSecondaryEmail(),
                e.getTitle(), e.getDepartment(),
                e.getSeniorityLevel() != null ? e.getSeniorityLevel().name() : null,
                e.getLocation(), e.getTimezone(), e.getLanguage(),
                e.getDomain(), e.getVerificationScore(), e.getSource(),
                e.getPrimaryDumpId(), e.getCompanyId(), resolveCompanyName(e.getCompanyId()),
                e.getNotes(),
                e.getTags() != null ? new ArrayList<>(e.getTags()) : new ArrayList<>(),
                e.getCustomFields() != null ? new HashMap<>(e.getCustomFields()) : new HashMap<>(),
                e.getAiEnrichment() != null ? new HashMap<>(e.getAiEnrichment()) : new HashMap<>(),
                e.getStatus() != null ? e.getStatus().name() : null,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    /**
     * Resolves a company's display name for {@link ContactResponse#companyName()}.
     * Previously this was hardcoded to {@code null} for every contact, so the
     * frontend's "group by company" view showed every single contact under
     * "Unknown" even when {@code companyId} was set correctly. A per-call
     * lookup is a known N+1 for list pages (one extra query per distinct
     * company on the page) — acceptable for now given typical page sizes;
     * worth batching via {@code findByIdIn} if this shows up in profiling.
     */
    private String resolveCompanyName(UUID companyId) {
        if (companyId == null) return null;
        return companyJpaRepository.findById(companyId).map(CompanyEntity::getDisplayName).orElse(null);
    }
}
