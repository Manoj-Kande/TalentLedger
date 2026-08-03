package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.UserSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserSessionEntity}.
 */
public interface JpaSessionRepository extends JpaRepository<UserSessionEntity, UUID> {

    /**
     * Find a session by its token hash.
     */
    Optional<UserSessionEntity> findBySessionTokenHash(String tokenHash);

    /**
     * Find all active (non-revoked) sessions for a user, ordered by most recent first.
     */
    List<UserSessionEntity> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID userId);

    /**
     * Count active, non-expired sessions for a user.
     */
    long countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, Instant now);

    /**
     * Find all non-revoked sessions for a user.
     */
    List<UserSessionEntity> findByUserIdAndRevokedAtIsNull(UUID userId);

    /**
     * Count all non-revoked sessions for a user.
     */
    int countByUserIdAndRevokedAtIsNull(UUID userId);
}
