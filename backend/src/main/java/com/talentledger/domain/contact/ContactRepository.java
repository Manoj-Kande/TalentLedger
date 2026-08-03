package com.talentledger.domain.contact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-side port for the Contact aggregate.
 *
 * <p>CQRS-lite: this interface exposes only query methods.
 * No framework imports — pure Java interface for hexagonal architecture.
 *
 * <p>Implementations live in the infrastructure (outbound) adapter layer.
 */
public interface ContactRepository {

    /**
     * Find a contact by its id, scoped to a specific user.
     *
     * @param id     the contact's primary key
     * @param userId the owning user's id
     * @return the contact, or empty if not found
     */
    Optional<Contact> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Find a contact by its raw email, scoped to a specific user.
     *
     * @param email  the raw email to match against
     * @param userId the owning user's id
     * @return the contact, or empty if not found
     */
    Optional<Contact> findByEmailAndUserId(String email, UUID userId);

    /**
     * Check whether a contact with the given raw email exists for a user.
     *
     * @param email  the raw email
     * @param userId the owning user's id
     * @return true if a matching contact exists
     */
    boolean existsByEmailAndUserId(String email, UUID userId);

    /**
     * Check whether a contact with the given normalized email exists for a user.
     *
     * @param normalizedEmail the canonical (normalized) email
     * @param userId          the owning user's id
     * @return true if a matching contact exists
     */
    boolean existsByNormalizedEmailAndUserId(String normalizedEmail, UUID userId);

    /**
     * Find all contacts for a user that have the given status.
     *
     * @param userId  the owning user's id
     * @param status the status to filter by
     * @return a list of matching contacts (never null)
     */
    List<Contact> findByUserIdAndStatus(UUID userId, ContactStatus status);

    /**
     * Find a contact by email, scoped to a specific user, excluding soft-deleted contacts.
     *
     * @param email  the raw email to match against
     * @param userId the owning user's id
     * @return the contact if found and not deleted, or empty
     */
    Optional<Contact> findByEmailAndUserIdExcludingDeleted(String email, UUID userId);
}
