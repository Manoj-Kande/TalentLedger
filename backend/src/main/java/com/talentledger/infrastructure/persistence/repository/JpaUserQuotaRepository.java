package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.UserQuotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserQuotaEntity}.
 */
public interface JpaUserQuotaRepository extends JpaRepository<UserQuotaEntity, UUID> {

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "UPDATE UserQuotaEntity q SET q.uploadsThisMonthCount = 0, q.aiCreditsUsed = 0, " +
            "q.lastResetAt = :now, q.updatedAt = :now")
    int resetMonthlyCounters(@org.springframework.data.repository.query.Param("now") java.time.Instant now);

    /**
     * Find the quota record for a given user.
     */
    Optional<UserQuotaEntity> findByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(q.storageBytesUsed), 0) FROM UserQuotaEntity q")
    long sumStorageBytesUsedAcrossAllUsers();
}
