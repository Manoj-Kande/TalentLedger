package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.domain.contact.ContactStatus;
import com.talentledger.domain.contact.SeniorityLevel;
import com.talentledger.infrastructure.persistence.entity.ContactEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ContactEntity}.
 */
public interface ContactJpaRepository extends JpaRepository<ContactEntity, UUID>,
        JpaSpecificationExecutor<ContactEntity> {

    Optional<ContactEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<ContactEntity> findByEmailAndUserId(String email, UUID userId);

    boolean existsByEmailAndUserId(String email, UUID userId);

    boolean existsByNormalizedEmailAndUserId(String normalizedEmail, UUID userId);

    List<ContactEntity> findByUserIdAndStatus(UUID userId, ContactStatus status);

    Optional<ContactEntity> findByEmailAndUserIdAndDeletedAtIsNull(String email, UUID userId);

    Optional<ContactEntity> findByNormalizedEmailAndUserIdAndDeletedAtIsNull(String normalizedEmail, UUID userId);

    // ── Search & cursor pagination ──────────────────────────────

    @Query("SELECT c FROM ContactEntity c WHERE c.userId = :userId AND c.deletedAt IS NULL " +
           "AND (:query IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:seniority IS NULL OR c.seniorityLevel = :seniority) " +
           "AND (:location IS NULL OR c.location = :location) " +
           "AND (:domain IS NULL OR c.domain = :domain) " +
           "AND (:companyId IS NULL OR c.companyId = :companyId) " +
           "ORDER BY c.createdAt DESC")
    List<ContactEntity> searchContacts(@Param("userId") UUID userId,
                                        @Param("query") String query,
                                        @Param("seniority") SeniorityLevel seniority,
                                        @Param("location") String location,
                                        @Param("domain") String domain,
                                        @Param("companyId") UUID companyId,
                                        Pageable pageable);

    // ── Cursor pagination (composite cursor: createdAt + id) ────
    @Query("SELECT c FROM ContactEntity c WHERE c.userId = :userId AND c.deletedAt IS NULL " +
           "AND (c.createdAt < :cursorAt OR (c.createdAt = :cursorAt AND c.id < :cursorId)) " +
           "ORDER BY c.createdAt DESC, c.id DESC")
    List<ContactEntity> findByUserIdCursor(@Param("userId") UUID userId,
                                          @Param("cursorAt") java.time.Instant cursorAt,
                                          @Param("cursorId") UUID cursorId,
                                          Pageable pageable);

    @Query("SELECT c FROM ContactEntity c WHERE c.userId = :userId AND c.deletedAt IS NULL " +
           "ORDER BY c.createdAt DESC, c.id DESC")
    List<ContactEntity> findByUserIdNewest(@Param("userId") UUID userId, Pageable pageable);

    // ── Company-related ───────────────────────────────────────

    @Query("SELECT DISTINCT c.companyId FROM ContactEntity c WHERE c.userId = :userId AND c.companyId IS NOT NULL AND c.deletedAt IS NULL")
    List<UUID> findDistinctCompanyIdsByUserId(@Param("userId") UUID userId);

    @Query("SELECT c FROM ContactEntity c WHERE c.companyId = :companyId AND c.userId = :userId AND c.deletedAt IS NULL " +
           "ORDER BY c.createdAt DESC")
    List<ContactEntity> findByCompanyIdAndUserId(@Param("companyId") UUID companyId,
                                                 @Param("userId") UUID userId,
                                                 Pageable pageable);

    @Query("SELECT c FROM ContactEntity c WHERE c.companyId = :companyId AND c.userId = :userId AND c.deletedAt IS NULL " +
           "AND (c.createdAt < :cursorAt OR (c.createdAt = :cursorAt AND c.id < :cursorId)) " +
           "ORDER BY c.createdAt DESC, c.id DESC")
    List<ContactEntity> findByCompanyIdAndUserIdCursor(@Param("companyId") UUID companyId,
                                                       @Param("userId") UUID userId,
                                                       @Param("cursorAt") java.time.Instant cursorAt,
                                                       @Param("cursorId") UUID cursorId,
                                                       Pageable pageable);

    long countByCompanyIdAndUserIdAndDeletedAtIsNull(UUID companyId, UUID userId);

    // ── Stats ─────────────────────────────────────────────────
    long countByUserIdAndDeletedAtIsNull(UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    // ── Tag operations ────────────────────────────────────────

    @Query(value = "SELECT DISTINCT t FROM contacts c, jsonb_array_elements_text(c.tags) AS t WHERE c.user_id = :userId AND c.deleted_at IS NULL", nativeQuery = true)
    List<String> findDistinctTagsByUserId(@Param("userId") UUID userId);

    // ── Duplicates ─────────────────────────────────────────────

    @Query("SELECT c FROM ContactEntity c WHERE c.userId = :userId AND c.deletedAt IS NULL " +
           "AND c.normalizedEmail IN (SELECT c2.normalizedEmail FROM ContactEntity c2 WHERE c2.userId = :userId AND c2.deletedAt IS NULL " +
           "AND c2.primaryDumpId != c.primaryDumpId) ORDER BY c.normalizedEmail")
    List<ContactEntity> findPotentialDuplicates(@Param("userId") UUID userId);

    // ── Guest-claim (item #1) ────────────────────────────────

    /** Bulk-reassign every contact owned by a guest account onto the real account claiming its data. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE ContactEntity c SET c.userId = :realUserId WHERE c.userId = :guestUserId")
    void reassignOwner(@Param("guestUserId") UUID guestUserId, @Param("realUserId") UUID realUserId);
}
