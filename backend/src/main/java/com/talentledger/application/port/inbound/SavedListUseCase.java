package com.talentledger.application.port.inbound;

import com.talentledger.application.dto.response.SavedListResponse;
import com.talentledger.application.dto.response.ContactResponse;
import com.talentledger.domain.shared.Result;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port — Saved List use cases.
 * Implemented by SavedListService in the application layer.
 */
public interface SavedListUseCase {

    /** List all saved lists for the user. */
    Result<List<SavedListResponse>, String> listLists(UUID userId);

    /** Get a single saved list by ID. */
    Result<SavedListDetailResult, String> getList(UUID listId, UUID userId);

    /** Create a new saved list. */
    Result<SavedListResponse, String> createList(UUID userId, CreateListCommand command);

    /** Update a saved list (rename, description, dynamic toggle). */
    Result<SavedListResponse, String> updateList(UUID listId, UUID userId, UpdateListCommand command);

    /** Delete a saved list. */
    Result<Void, String> deleteList(UUID listId, UUID userId);

    /** Add contacts to a list (bulk). */
    Result<BulkAddResult, String> addContactsToList(UUID listId, UUID userId, List<UUID> contactIds);

    /** Remove a contact from a list. */
    Result<Void, String> removeContactFromList(UUID listId, UUID userId, UUID contactId);

    /** Get contacts within a saved list (cursor paginated). */
    Result<ListContactsResult, String> getListContacts(UUID listId, UUID userId, String cursor, int size);

    // ── Inner types ──────────────────────────────────────

    record CreateListCommand(String name, String description) {}
    record UpdateListCommand(String name, String description, Boolean isDynamic) {}

    record SavedListDetailResult(SavedListResponse list, List<ContactResponse> contacts, String nextCursor, boolean hasMore) {}
    record ListContactsResult(List<ContactResponse> contacts, String nextCursor, boolean hasMore) {}
    record BulkAddResult(int added, int skipped, int failed) {}
}
