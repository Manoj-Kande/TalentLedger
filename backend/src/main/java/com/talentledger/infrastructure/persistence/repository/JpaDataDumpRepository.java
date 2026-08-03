package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.DataDumpEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link DataDumpEntity}.
 */
public interface JpaDataDumpRepository extends JpaRepository<DataDumpEntity, UUID> {

    /**
     * Find all non-deleted dumps for a user, ordered by most recent first.
     */
    Page<DataDumpEntity> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find all non-deleted dumps for a user.
     */
    List<DataDumpEntity> findByUserIdAndDeletedAtIsNull(UUID userId);

    /**
     * Find a specific non-deleted dump by id, scoped to a user.
     */
    Optional<DataDumpEntity> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /**
     * Check whether a non-deleted dump with the given file hash exists for a user.
     */
    boolean existsByUserIdAndFileHashAndDeletedAtIsNull(UUID userId, String fileHash);

    /**
     * Find non-persisted, expired dumps that have not been deleted.
     * Used by the cleanup job to purge ephemeral dump data.
     */
    List<DataDumpEntity> findByIsPersistedFalseAndExpiresAtBeforeAndDeletedAtIsNull(Instant now);

    /**
     * Find all pinned, non-deleted dumps for a user.
     */
    List<DataDumpEntity> findByUserIdAndIsPinnedTrueAndDeletedAtIsNull(UUID userId);

    /**
     * Find a dump by id, scoped to a user (including soft-deleted).
     */
    Optional<DataDumpEntity> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Check whether a dump exists by id, scoped to a user.
     */
    boolean existsByIdAndUserId(UUID id, UUID userId);

    /**
     * Check whether a dump with the given file hash exists for a user (including soft-deleted).
     */
    boolean existsByUserIdAndFileHash(UUID userId, String fileHash);

    /**
     * Find all dumps for a user, ordered by most recent first (including soft-deleted).
     */
    List<DataDumpEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find non-persisted dumps whose expiry time has passed (including soft-deleted).
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT d FROM DataDumpEntity d WHERE d.isPersisted = false AND d.expiresAt IS NOT NULL AND d.expiresAt < :now")
    List<DataDumpEntity> findExpiredFreeDumps(@org.springframework.data.repository.query.Param("now") Instant now);

    @org.springframework.data.jpa.repository.Query("SELECT d.originalFileKey FROM DataDumpEntity d WHERE d.originalFileKey IS NOT NULL")
    List<String> findAllOriginalFileKeys();

    long countByUserIdAndDeletedAtIsNull(UUID userId);
}
