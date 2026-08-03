package com.talentledger.domain.contact;

import java.util.List;

/**
 * Write-side port for the Contact aggregate.
 *
 * <p>CQRS-lite: this interface exposes only mutation methods.
 * No framework imports — pure Java interface for hexagonal architecture.
 *
 * <p>Implementations live in the infrastructure (outbound) adapter layer.
 */
public interface ContactCommandRepository {

    /**
     * Persist a contact aggregate. May insert or update depending on
     * implementation (e.g. upsert semantics).
     *
     * @param contact the contact to save (must not be null)
     * @return the persisted contact (potentially with updated metadata)
     */
    Contact save(Contact contact);

    /**
     * Persist a batch of contacts atomically or in a single transaction.
     *
     * @param contacts the contacts to save (must not be null or contain nulls)
     */
    void saveAll(List<Contact> contacts);

    /**
     * Permanently delete a contact from the persistence store.
     *
     * <p>Use this for hard deletes. Soft deletes are handled by the
     * aggregate itself via {@link Contact#softDelete()}.
     *
     * @param contact the contact to permanently remove
     */
    void delete(Contact contact);
}
