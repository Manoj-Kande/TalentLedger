package com.talentledger.infrastructure.web.controller;

import com.talentledger.application.dto.request.CreateContactRequest;
import com.talentledger.application.dto.request.UpdateContactRequest;
import com.talentledger.application.port.inbound.ContactUseCase;
import com.talentledger.infrastructure.persistence.entity.ContactEntity;
import com.talentledger.infrastructure.persistence.repository.ContactJpaRepository;
import com.talentledger.infrastructure.persistence.repository.JpaOutreachEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Contact Controller — search, CRUD, bulk operations, notes, tags, duplicates.
 */
@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactUseCase contactUseCase;
    private final ContactJpaRepository contactJpaRepository;
    private final JpaOutreachEventRepository outreachEventRepository;

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "sort", defaultValue = "name_asc") String sortBy,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "seniority", required = false) String seniority,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "company", required = false) String company,
            HttpServletRequest httpRequest) {

        UUID userId = getCurrentUserId(httpRequest);

        java.util.List<ContactUseCase.FilterCondition> filters = new java.util.ArrayList<>();
        if (seniority != null) filters.add(new ContactUseCase.FilterCondition("seniorityLevel", "eq", seniority));
        if (location != null) filters.add(new ContactUseCase.FilterCondition("location", "eq", location));
        if (domain != null) filters.add(new ContactUseCase.FilterCondition("domain", "eq", domain));
        if (company != null) filters.add(new ContactUseCase.FilterCondition("company", "eq", company));

        var searchQuery = new ContactUseCase.ContactSearchQuery(query, sortBy, "asc", cursor, size, filters);
        var result = contactUseCase.searchContacts(userId, searchQuery);

        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", result.getValue()))
                : ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = contactUseCase.getContact(id, userId);
        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", result.getValue()))
                : ResponseEntity.status(404).body(Map.of("success", false, "error", result.getError()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateContactRequest request,
                                                     HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var command = new ContactUseCase.CreateContactCommand(
                request.name(), request.email(), request.phone(), request.linkedinUrl(),
                request.secondaryEmail(), request.title(), request.department(),
                request.seniorityLevel(), request.location(), request.timezone(),
                request.language(), request.notes(), request.tags(),
                request.primaryDumpId(), request.companyId());
        var result = contactUseCase.createContact(userId, command);
        return result.isSuccess()
                ? ResponseEntity.status(201).body(Map.of("success", true, "data", result.getValue()))
                : ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID id,
            @RequestBody UpdateContactRequest request,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var command = new ContactUseCase.UpdateContactCommand(
                request.name(), request.phone(), request.linkedinUrl(),
                request.secondaryEmail(), request.title(), request.department(),
                request.seniorityLevel(), request.location(), request.timezone(),
                request.language(), request.notes(), request.tags(),
                request.customFields());
        var result = contactUseCase.updateContact(id, userId, command);
        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", result.getValue()))
                : ResponseEntity.status(404).body(Map.of("success", false, "error", result.getError()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = contactUseCase.deleteContact(id, userId);
        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", Map.of("message", "Contact deleted")))
                : ResponseEntity.status(404).body(Map.of("success", false, "error", result.getError()));
    }

    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulk(@RequestBody ContactUseCase.BulkOperationCommand command,
                                                    HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = contactUseCase.bulkOperation(userId, command);
        return ResponseEntity.ok(Map.of("success", true, "data", result.getValue()));
    }

    // ── Notes ─────────────────────────────────────────────────

    @PatchMapping("/{id}/notes")
    public ResponseEntity<Map<String, Object>> updateNotes(@PathVariable UUID id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        return contactJpaRepository.findByIdAndUserId(id, userId)
                .map(entity -> {
                    entity.setNotes(body.get("notes"));
                    entity.setUpdatedAt(java.time.Instant.now());
                    contactJpaRepository.save(entity);
                    return ResponseEntity.ok(Map.of("success", true, "data", Map.of("notes", entity.getNotes())));
                })
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("success", false, "error", Map.of("message", "Contact not found"))));
    }

    // ── Tags ──────────────────────────────────────────────────

    @PostMapping("/{id}/tags")
    public ResponseEntity<Map<String, Object>> addTag(@PathVariable UUID id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        return contactJpaRepository.findByIdAndUserId(id, userId)
                .map(entity -> {
                    List<String> tags = entity.getTags() != null ? new ArrayList<>(entity.getTags()) : new ArrayList<>();
                    String tag = body.get("tag");
                    if (tag != null && !tags.contains(tag)) {
                        tags.add(tag);
                        entity.setTags(tags);
                        entity.setUpdatedAt(java.time.Instant.now());
                        contactJpaRepository.save(entity);
                    }
                    return ResponseEntity.ok(Map.of("success", true, "data", Map.of("tags", entity.getTags())));
                })
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("success", false, "error", Map.of("message", "Contact not found"))));
    }

    @DeleteMapping("/{id}/tags/{tag}")
    public ResponseEntity<Map<String, Object>> removeTag(@PathVariable UUID id,
            @PathVariable String tag,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        return contactJpaRepository.findByIdAndUserId(id, userId)
                .map(entity -> {
                    List<String> tags = entity.getTags() != null ? new ArrayList<>(entity.getTags()) : new ArrayList<>();
                    tags.remove(tag);
                    entity.setTags(tags);
                    entity.setUpdatedAt(java.time.Instant.now());
                    contactJpaRepository.save(entity);
                    return ResponseEntity.ok(Map.of("success", true, "data", Map.of("tags", entity.getTags())));
                })
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("success", false, "error", Map.of("message", "Contact not found"))));
    }

    @GetMapping("/tags")
    public ResponseEntity<Map<String, Object>> getAllTags(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        List<String> tags = contactJpaRepository.findDistinctTagsByUserId(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", tags));
    }

    // ── Duplicates ──────────────────────────────────────────

    @GetMapping("/duplicates")
    public ResponseEntity<Map<String, Object>> getDuplicates(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        List<ContactEntity> dupes = contactJpaRepository.findPotentialDuplicates(userId);
        var contacts = dupes.stream()
                .map(e -> new com.talentledger.application.dto.response.ContactResponse(
                        e.getId(), e.getName(),
                        e.getEmail(), e.getNormalizedEmail(),
                        e.getPhone(), e.getLinkedinUrl(), e.getSecondaryEmail(),
                        e.getTitle(), e.getDepartment(),
                        e.getSeniorityLevel() != null ? e.getSeniorityLevel().name() : null,
                        e.getLocation(), e.getTimezone(), e.getLanguage(),
                        e.getDomain(), e.getVerificationScore(), e.getSource(),
                        e.getPrimaryDumpId(), e.getCompanyId(), null,
                        e.getNotes(),
                        e.getTags() != null ? new ArrayList<>(e.getTags()) : new ArrayList<>(),
                        e.getCustomFields() != null ? new HashMap<>(e.getCustomFields()) : new HashMap<>(),
                        e.getAiEnrichment() != null ? new HashMap<>(e.getAiEnrichment()) : new HashMap<>(),
                        e.getStatus() != null ? e.getStatus().name() : null,
                        e.getCreatedAt(), e.getUpdatedAt()))
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "data", contacts, "total", contacts.size()));
    }

    // ── Search Suggestions ──────────────────────────────────

    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> getSuggestions(
            @RequestParam(value = "type") String type,
            @RequestParam(value = "q", required = false) String query,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        List<Map<String, Object>> suggestions = new ArrayList<>();

        return switch (type) {
            case "tag" -> {
                List<String> tags = contactJpaRepository.findDistinctTagsByUserId(userId);
                List<String> filtered = query != null
                        ? tags.stream().filter(t -> t.toLowerCase().startsWith(query.toLowerCase())).limit(10).toList()
                        : tags.stream().limit(10).toList();
                yield ResponseEntity.ok(Map.of("success", true, "data", filtered));
            }
            default -> ResponseEntity.ok(Map.of("success", true, "data", suggestions));
        };
    }

    // ── AI Enrichment (Pro/Team only) ────────────────────

    @PostMapping("/{id}/enrich")
    public ResponseEntity<Map<String, Object>> enrich(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var command = new ContactUseCase.EnrichContactCommand(body.get("type"), body.get("tone"));
        var result = contactUseCase.enrichContact(id, userId, command);

        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", result.getValue()))
                : ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
    }

    // ── Outreach Events ──────────────────────────────────

    @GetMapping("/{id}/events")
    public ResponseEntity<Map<String, Object>> getContactEvents(
            @PathVariable UUID id,
            @RequestParam(value = "size", defaultValue = "50") int size,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        if (!contactJpaRepository.existsByIdAndUserId(id, userId)) {
            return ResponseEntity.status(404)
                    .body(Map.of("success", false, "error", Map.of("message", "Contact not found")));
        }
        List<com.talentledger.infrastructure.persistence.entity.OutreachEventEntity> events =
                outreachEventRepository.findByContactIdAndDeletedAtIsNullOrderByOccurredAtDesc(id)
                        .stream().limit(size).toList();
        List<Map<String, Object>> eventMaps = events.stream().map(e -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("eventType", e.getEventType() != null ? e.getEventType().name() : null);
            m.put("status", e.getStatus());
            m.put("content", e.getContent());
            m.put("sentiment", e.getSentiment() != null ? e.getSentiment().name() : null);
            m.put("occurredAt", e.getOccurredAt());
            m.put("campaignId", e.getCampaignId());
            m.put("createdAt", e.getCreatedAt());
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("success", true, "data", eventMaps, "total", eventMaps.size()));
    }

    // ── Bulk add to saved list via contacts endpoint ──────

    private UUID getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new com.talentledger.shared.exception.UnauthorizedException(
                    "No authenticated user on request (SessionAuthFilter should have rejected this earlier)");
        }
        return UUID.fromString(userId.toString());
    }
}
