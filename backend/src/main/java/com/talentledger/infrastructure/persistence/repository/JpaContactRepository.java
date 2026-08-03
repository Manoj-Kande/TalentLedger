package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.domain.contact.ContactStatus;
import com.talentledger.infrastructure.persistence.entity.ContactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ContactEntity}.
 */
public interface JpaContactRepository extends JpaRepository<ContactEntity, UUID> {

    Optional<ContactEntity> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    Optional<ContactEntity> findByEmailAndUserIdAndDeletedAtIsNull(String email, UUID userId);

    boolean existsByEmailAndUserIdAndDeletedAtIsNull(String email, UUID userId);

    boolean existsByNormalizedEmailAndUserIdAndDeletedAtIsNull(String normalizedEmail, UUID userId);

    List<ContactEntity> findByUserIdAndStatusAndDeletedAtIsNull(UUID userId, ContactStatus status);

    @Query(value = """
            SELECT c.* FROM contacts c
            WHERE c.user_id = :userId AND c.deleted_at IS NULL
              AND (:search IS NULL OR c.name ILIKE CONCAT(:search, '%%'))
              AND (:lastValue IS NULL OR (c.name, c.id) < (:lastValue, :lastId))
            ORDER BY c.name, c.id LIMIT :limit
            """, nativeQuery = true)
    List<ContactEntity> findByNameCursor(@Param("userId") UUID userId, @Param("search") String search, @Param("lastValue") String lastValue, @Param("lastId") UUID lastId, @Param("limit") int limit);

    @Query(value = """
            SELECT c.* FROM contacts c
            WHERE c.user_id = :userId AND c.deleted_at IS NULL
              AND (:search IS NULL OR c.domain ILIKE CONCAT(:search, '%%'))
              AND (:lastValue IS NULL OR (c.domain, c.id) < (:lastValue, :lastId))
            ORDER BY c.domain, c.id LIMIT :limit
            """, nativeQuery = true)
    List<ContactEntity> findByCompanyDomainCursor(@Param("userId") UUID userId, @Param("search") String search, @Param("lastValue") String lastValue, @Param("lastId") UUID lastId, @Param("limit") int limit);

    @Query(value = """
            SELECT c.* FROM contacts c
            WHERE c.user_id = :userId AND c.deleted_at IS NULL
              AND (:search IS NULL OR c.title ILIKE CONCAT(:search, '%%'))
              AND (:lastValue IS NULL OR (c.title, c.id) < (:lastValue, :lastId))
            ORDER BY c.title, c.id LIMIT :limit
            """, nativeQuery = true)
    List<ContactEntity> findByTitleCursor(@Param("userId") UUID userId, @Param("search") String search, @Param("lastValue") String lastValue, @Param("lastId") UUID lastId, @Param("limit") int limit);

    @Query(value = """
            SELECT c.* FROM contacts c
            WHERE c.user_id = :userId AND c.deleted_at IS NULL
              AND (:lastValue IS NULL OR (c.verification_score, c.id) < (:lastValue, :lastId))
            ORDER BY c.verification_score DESC, c.id DESC LIMIT :limit
            """, nativeQuery = true)
    List<ContactEntity> findByVerificationScoreCursor(@Param("userId") UUID userId, @Param("lastValue") Integer lastValue, @Param("lastId") UUID lastId, @Param("limit") int limit);
}
