package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.domain.user.UserStatus;
import com.talentledger.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserEntity}.
 */
public interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Find a user by email (including soft-deleted).
     */
    Optional<UserEntity> findByEmail(String email);

    /**
     * Find a user by email, excluding soft-deleted records.
     */
    Optional<UserEntity> findByEmailAndDeletedAtIsNull(String email);

    /**
     * Find a user by Clerk authentication ID (including soft-deleted).
     */
    Optional<UserEntity> findByClerkId(String clerkId);

    /**
     * Find a user by Clerk authentication ID, excluding soft-deleted records.
     */
    Optional<UserEntity> findByClerkIdAndDeletedAtIsNull(String clerkId);

    /**
     * Find users by status, excluding soft-deleted records, with pagination.
     */
    Page<UserEntity> findByStatusAndDeletedAtIsNull(UserStatus status, Pageable pageable);

    /**
     * Check whether a non-deleted user with the given email exists.
     */
    boolean existsByEmailAndDeletedAtIsNull(String email);

    /**
     * Check whether a user with the given email exists (including soft-deleted).
     */
    boolean existsByEmail(String email);

    /** Guest accounts (item #1) past their TTL — see ScheduledJobs.purgeExpiredGuestAccounts. */
    java.util.List<UserEntity> findByIsGuestTrueAndGuestExpiresAtBefore(java.time.Instant now);
}
