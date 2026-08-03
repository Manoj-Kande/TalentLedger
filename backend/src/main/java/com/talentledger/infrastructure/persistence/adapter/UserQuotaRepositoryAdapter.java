package com.talentledger.infrastructure.persistence.adapter;

import com.talentledger.domain.user.UserQuota;
import com.talentledger.domain.user.UserQuotaRepository;
import com.talentledger.infrastructure.persistence.entity.UserQuotaEntity;
import com.talentledger.infrastructure.persistence.repository.JpaUserQuotaRepository;
import com.talentledger.infrastructure.persistence.repository.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that bridges the {@link UserQuotaRepository} domain port
 * to the Spring Data JPA {@link com.talentledger.infrastructure.persistence.repository.JpaUserQuotaRepository}.
 *
 * <p>{@link UserQuota} is an immutable value object with a public {@link
 * com.talentledger.domain.user.UserQuota.Builder} that accepts all fields
 * directly, so no reflection is required for reconstitution.
 *
 * <p>{@link UserQuotaEntity#user} is mapped with {@code @MapsId}, so Hibernate
 * derives the shared primary key from the *associated entity*, not from the
 * raw {@code userId} field. A managed reference to {@link
 * com.talentledger.infrastructure.persistence.entity.UserEntity} must always
 * be attached before saving a brand-new quota row, or Hibernate throws
 * "attempted to assign id from null one-to-one property" and the enclosing
 * transaction (e.g. registration) rolls back.
 *
 * <p>{@code save()} is used both to create the very first quota row (at
 * registration) and to update counters later (e.g. after a dump upload —
 * which typically already loaded the row earlier in the same transaction
 * via {@link #findByUserId}, putting a managed {@code UserQuotaEntity} into
 * the Hibernate session). Always building a fresh entity via the builder for
 * both cases caused a {@code NonUniqueObjectException} on update: a second,
 * distinct Java object with the same id was already associated with the
 * session. To avoid this, {@code save()} first checks for an existing row —
 * if found, its fields are updated in place (going through {@code merge()});
 * only when the row genuinely doesn't exist yet is a new entity built and
 * persisted.
 */
@Component
@RequiredArgsConstructor
public class UserQuotaRepositoryAdapter implements UserQuotaRepository {

    private final JpaUserQuotaRepository jpaRepository;
    private final JpaUserRepository userJpaRepository;

    // ── Query methods ────────────────────────────────────────────

    @Override
    public Optional<UserQuota> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).map(this::toDomain);
    }

    // ── Mutation methods ──────────────────────────────────────────

    @Override
    @Transactional
    public UserQuota save(UserQuota quota) {
        UserQuotaEntity entity = jpaRepository.findById(quota.getUserId())
                .map(existing -> applyFields(existing, quota))
                .orElseGet(() -> toEntity(quota));

        UserQuotaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    // ── Domain ← Entity ──────────────────────────────────────────

    private UserQuota toDomain(UserQuotaEntity entity) {
        return new UserQuota.Builder()
                .userId(entity.getUserId())
                .activeDumpsCount(entity.getActiveDumpsCount())
                .activeDumpsLimit(entity.getActiveDumpsLimit())
                .contactsStoredCount(entity.getContactsStoredCount())
                .contactsStoredLimit(entity.getContactsStoredLimit())
                .uploadsThisMonthCount(entity.getUploadsThisMonthCount())
                .uploadsMonthlyLimit(entity.getUploadsMonthlyLimit())
                .aiCreditsUsed(entity.getAiCreditsUsed())
                .aiCreditsLimit(entity.getAiCreditsLimit())
                .storageBytesUsed(entity.getStorageBytesUsed())
                .storageBytesLimit(entity.getStorageBytesLimit())
                .hasActiveFreeDump(toBool(entity.getHasActiveFreeDump()))
                .lastResetAt(entity.getLastResetAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // ── Entity ← Domain (INSERT path — row does not exist yet) ────

    private UserQuotaEntity toEntity(UserQuota quota) {
        return UserQuotaEntity.builder()
                // Required for @MapsId to resolve the shared PK on insert.
                // getReferenceById gives Hibernate a lazy proxy (no extra
                // SELECT) that carries the id @MapsId needs.
                .user(userJpaRepository.getReferenceById(quota.getUserId()))
                .userId(quota.getUserId())
                .activeDumpsCount(quota.getActiveDumpsCount())
                .activeDumpsLimit(quota.getActiveDumpsLimit())
                .contactsStoredCount(quota.getContactsStoredCount())
                .contactsStoredLimit(quota.getContactsStoredLimit())
                .uploadsThisMonthCount(quota.getUploadsThisMonthCount())
                .uploadsMonthlyLimit(quota.getUploadsMonthlyLimit())
                .aiCreditsUsed(quota.getAiCreditsUsed())
                .aiCreditsLimit(quota.getAiCreditsLimit())
                .storageBytesUsed(quota.getStorageBytesUsed())
                .storageBytesLimit(quota.getStorageBytesLimit())
                .hasActiveFreeDump(quota.isHasActiveFreeDump())
                .lastResetAt(quota.getLastResetAt())
                .updatedAt(quota.getUpdatedAt())
                .build();
    }

    // ── Entity ← Domain (UPDATE path — row already exists) ────────

    /**
     * Copies every mutable field from the domain {@link UserQuota} onto an
     * already-persisted (possibly already-managed-in-session) entity, rather
     * than constructing a new one. Deliberately leaves {@code userId} and
     * {@code user} untouched — they never change after the row is created.
     */
    private UserQuotaEntity applyFields(UserQuotaEntity existing, UserQuota quota) {
        existing.setActiveDumpsCount(quota.getActiveDumpsCount());
        existing.setActiveDumpsLimit(quota.getActiveDumpsLimit());
        existing.setContactsStoredCount(quota.getContactsStoredCount());
        existing.setContactsStoredLimit(quota.getContactsStoredLimit());
        existing.setUploadsThisMonthCount(quota.getUploadsThisMonthCount());
        existing.setUploadsMonthlyLimit(quota.getUploadsMonthlyLimit());
        existing.setAiCreditsUsed(quota.getAiCreditsUsed());
        existing.setAiCreditsLimit(quota.getAiCreditsLimit());
        existing.setStorageBytesUsed(quota.getStorageBytesUsed());
        existing.setStorageBytesLimit(quota.getStorageBytesLimit());
        existing.setHasActiveFreeDump(quota.isHasActiveFreeDump());
        existing.setLastResetAt(quota.getLastResetAt());
        existing.setUpdatedAt(quota.getUpdatedAt());
        return existing;
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static boolean toBool(Boolean v) {
        return v != null && v;
    }
}