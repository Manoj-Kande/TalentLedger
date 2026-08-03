package com.talentledger.infrastructure.persistence.adapter;

import com.talentledger.domain.contact.Contact;
import com.talentledger.domain.contact.ContactCommandRepository;
import com.talentledger.infrastructure.persistence.entity.ContactEntity;
import com.talentledger.infrastructure.persistence.repository.ContactJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persistence adapter that bridges the write-side {@link ContactCommandRepository}
 * domain port to the Spring Data JPA {@link ContactJpaRepository}.
 *
 * <p>Delegates the {@link #toDomain(ContactEntity)} conversion to
 * {@link ContactRepositoryAdapter#toDomain(ContactEntity)} to avoid duplicating
 * the reflection-based reconstitution logic.
 */
@Component
@RequiredArgsConstructor
public class ContactCommandRepositoryAdapter implements ContactCommandRepository {

    private final ContactJpaRepository jpaRepository;
    private final ContactRepositoryAdapter readAdapter;

    // ── Mutation methods ──────────────────────────────────────────

    @Override
    @Transactional
    public Contact save(Contact contact) {
        ContactEntity entity = toEntity(contact);
        ContactEntity saved = jpaRepository.save(entity);
        return readAdapter.toDomain(saved);
    }

    @Override
    @Transactional
    public void saveAll(List<Contact> contacts) {
        List<ContactEntity> entities = contacts.stream()
                .map(this::toEntity)
                .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    @Transactional
    public void delete(Contact contact) {
        ContactEntity entity = toEntity(contact);
        jpaRepository.delete(entity);
    }

    // ── Entity ← Domain ──────────────────────────────────────────

    private ContactEntity toEntity(Contact contact) {
        return ContactEntity.builder()
                .id(contact.getId())
                .userId(contact.getUserId())
                .primaryDumpId(contact.getPrimaryDumpId())
                .companyId(contact.getCompanyId())
                .name(contact.getName())
                .email(contact.getEmail() != null ? contact.getEmail().getValue() : null)
                .normalizedEmail(contact.getNormalizedEmail() != null ? contact.getNormalizedEmail().getValue() : null)
                .phone(contact.getPhone())
                .linkedinUrl(contact.getLinkedinUrl())
                .secondaryEmail(contact.getSecondaryEmail())
                .title(contact.getTitle())
                .department(contact.getDepartment())
                .seniorityLevel(contact.getSeniorityLevel())
                .location(contact.getLocation())
                .timezone(contact.getTimezone())
                .language(contact.getLanguage())
                .domain(contact.getDomain())
                .verificationScore(contact.getVerificationScore())
                .lastActivityDate(contact.getLastActivityDate())
                .sourceUrl(contact.getSourceUrl())
                .source(contact.getSource())
                .notes(contact.getNotes())
                .tags(contact.getTags())
                .customFields(contact.getCustomFields())
                .aiEnrichment(contact.getAiEnrichment())
                .status(contact.getStatus())
                .createdAt(contact.getCreatedAt())
                .updatedAt(contact.getUpdatedAt())
                .deletedAt(contact.getDeletedAt())
                .build();
    }
}
