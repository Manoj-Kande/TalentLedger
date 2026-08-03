package com.talentledger.infrastructure.persistence.adapter;

import com.talentledger.domain.contact.Contact;
import com.talentledger.domain.contact.ContactRepository;
import com.talentledger.domain.contact.ContactStatus;
import com.talentledger.domain.contact.Email;
import com.talentledger.domain.contact.NormalizedEmail;
import com.talentledger.infrastructure.persistence.entity.ContactEntity;
import com.talentledger.infrastructure.persistence.repository.ContactJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that bridges the read-side {@link ContactRepository}
 * domain port to the Spring Data JPA {@link ContactJpaRepository}.
 *
 * <p>Uses reflection to reconstitute the immutable {@link Contact} aggregate
 * from JPA entities, since the domain class has a private constructor and
 * no publicly-accessible hydration API.
 */
@Component
@RequiredArgsConstructor
public class ContactRepositoryAdapter implements ContactRepository {

    private final ContactJpaRepository jpaRepository;

    // ── Query methods ────────────────────────────────────────────

    @Override
    public Optional<Contact> findByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    public Optional<Contact> findByEmailAndUserId(String email, UUID userId) {
        return jpaRepository.findByEmailAndUserId(email, userId).map(this::toDomain);
    }

    @Override
    public boolean existsByEmailAndUserId(String email, UUID userId) {
        return jpaRepository.existsByEmailAndUserId(email, userId);
    }

    @Override
    public boolean existsByNormalizedEmailAndUserId(String normalizedEmail, UUID userId) {
        return jpaRepository.existsByNormalizedEmailAndUserId(normalizedEmail, userId);
    }

    @Override
    public List<Contact> findByUserIdAndStatus(UUID userId, ContactStatus status) {
        return jpaRepository.findByUserIdAndStatus(userId, status)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Contact> findByEmailAndUserIdExcludingDeleted(String email, UUID userId) {
        return jpaRepository.findByEmailAndUserIdAndDeletedAtIsNull(email, userId)
                .map(this::toDomain);
    }

    // ── Domain ← Entity ──────────────────────────────────────────

    public Contact toDomain(ContactEntity entity) {
        try {
            Constructor<Contact> ctor = Contact.class.getDeclaredConstructor(UUID.class);
            ctor.setAccessible(true);
            Contact contact = ctor.newInstance(entity.getId());

            setField(contact, "userId", entity.getUserId());
            setField(contact, "primaryDumpId", entity.getPrimaryDumpId());
            setField(contact, "companyId", entity.getCompanyId());
            setField(contact, "name", entity.getName());

            // Reconstitute Email value object
            if (entity.getEmail() != null) {
                setField(contact, "email", Email.of(entity.getEmail()));
            }

            // Reconstitute NormalizedEmail value object
            if (entity.getNormalizedEmail() != null) {
                setField(contact, "normalizedEmail", NormalizedEmail.of(entity.getNormalizedEmail()));
            }

            setField(contact, "phone", entity.getPhone());
            setField(contact, "linkedinUrl", entity.getLinkedinUrl());
            setField(contact, "secondaryEmail", entity.getSecondaryEmail());
            setField(contact, "title", entity.getTitle());
            setField(contact, "department", entity.getDepartment());
            setField(contact, "seniorityLevel", entity.getSeniorityLevel());
            setField(contact, "location", entity.getLocation());
            setField(contact, "timezone", entity.getTimezone());
            setField(contact, "language", entity.getLanguage());
            setField(contact, "domain", entity.getDomain());
            setField(contact, "verificationScore", entity.getVerificationScore());
            setField(contact, "lastActivityDate", entity.getLastActivityDate());
            setField(contact, "sourceUrl", entity.getSourceUrl());
            setField(contact, "source", entity.getSource());
            setField(contact, "notes", entity.getNotes());
            setField(contact, "tags", entity.getTags() != null ? new ArrayList<>(entity.getTags()) : new ArrayList<>());
            setField(contact, "customFields",
                    entity.getCustomFields() != null ? new HashMap<>(entity.getCustomFields()) : new HashMap<>());
            setField(contact, "aiEnrichment",
                    entity.getAiEnrichment() != null ? new HashMap<>(entity.getAiEnrichment()) : new HashMap<>());
            setField(contact, "status", entity.getStatus());

            // AggregateRoot fields
            setField(contact, "createdAt", entity.getCreatedAt());
            setField(contact, "updatedAt", entity.getUpdatedAt());
            setField(contact, "deletedAt", entity.getDeletedAt());

            return contact;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reconstitute Contact aggregate from entity", e);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Walk up the class hierarchy to find a declared field. */
    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + clazz.getName() + " hierarchy");
    }
}
