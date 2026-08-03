package com.talentledger.domain.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port (repository interface) for the User aggregate.
 *
 * <p>Implementations live in the infrastructure (outbound) adapter layer.
 * Pure Java interface — zero framework annotations.
 */
public interface UserRepository {

    /**
     * Find a user by primary key.
     *
     * @param id the user's UUID
     * @return the user, or empty if not found
     */
    Optional<User> findById(UUID id);

    /**
     * Find a user by email address.
     *
     * @param email the email to look up
     * @return the user, or empty if not found
     */
    Optional<User> findByEmail(String email);

    /**
     * Find a user by their Clerk authentication ID.
     *
     * @param clerkId the Clerk user ID
     * @return the user, or empty if not found or clerkId is null
     */
    Optional<User> findByClerkId(String clerkId);

    /**
     * Persist a user aggregate. May insert or update depending on
     * implementation (e.g. upsert semantics).
     *
     * @param user the user to save (must not be null)
     * @return the persisted user (potentially with updated metadata)
     */
    User save(User user);

    /**
     * Check whether a user with the given email exists (including soft-deleted).
     *
     * @param email the email to check
     * @return true if a matching user exists
     */
    boolean existsByEmail(String email);

    /**
     * Check whether a non-deleted user with the given email exists.
     *
     * @param email the email to check
     * @return true if a matching active user exists
     */
    boolean existsByEmailExcludingDeleted(String email);

    /**
     * Find all users.
     *
     * @return all users (never null)
     */
    List<User> findAll();
}
