package com.talentledger.application.port.inbound;

import com.talentledger.application.dto.response.ContactResponse;
import com.talentledger.domain.shared.Result;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound port — Contact use cases.
 * Implemented by ContactService in the application layer.
 */
public interface ContactUseCase {

    /** Get a single contact by ID with ownership check. */
    Result<ContactResponse, String> getContact(UUID contactId, UUID userId);

    /** Cross-dump search with field-segregated filters and cursor pagination. */
    Result<ContactSearchResult, String> searchContacts(UUID userId, ContactSearchQuery query);

    /** Manually add a single contact (premium only). */
    Result<ContactResponse, String> createContact(UUID userId, CreateContactCommand command);

    /** Update a contact (premium only). */
    Result<ContactResponse, String> updateContact(UUID contactId, UUID userId, UpdateContactCommand command);

    /** Soft-delete a contact (premium only). */
    Result<Void, String> deleteContact(UUID contactId, UUID userId);

    /** Bulk operations (upsert, delete, archive — max 500). */
    Result<BulkOperationResult, String> bulkOperation(UUID userId, BulkOperationCommand command);

    /** AI-enrich a contact (Pro/Team only — gated on ai_credits quota). Costs 1 AI credit. */
    Result<EnrichmentResult, String> enrichContact(UUID contactId, UUID userId, EnrichContactCommand command);

    // ── Inner types (to avoid domain leakage into port) ────

    sealed interface ContactSearchResult permits CursorPage {}

    record CursorPage(List<ContactResponse> contacts, String nextCursor, boolean hasMore)
            implements ContactSearchResult {}

    record ContactSearchQuery(
        String query,
        String sortBy,
        String sortDirection,
        String nextCursor,
        int pageSize,
        List<FilterCondition> filters
    ) {}

    record FilterCondition(String field, String operator, Object value) {}

    record CreateContactCommand(
        String name, String email, String phone, String linkedinUrl,
        String secondaryEmail, String title, String department,
        String seniorityLevel, String location, String timezone,
        String language, String notes, List<String> tags,
        UUID primaryDumpId, UUID companyId
    ) {}

    record UpdateContactCommand(
        String name, String phone, String linkedinUrl,
        String secondaryEmail, String title, String department,
        String seniorityLevel, String location, String timezone,
        String language, String notes, List<String> tags,
        Map<String, Object> customFields
    ) {}

    record BulkOperationCommand(
        BulkOperationType type,
        List<UUID> contactIds,
        CreateContactCommand createTemplate
    ) {}

    enum BulkOperationType { UPSERT, DELETE, ARCHIVE, UNARCHIVE }

    record BulkOperationResult(int processed, int skipped, int failed, List<String> errors) {}

    /** type: one of EMAIL_DRAFT, HIRING_SIGNAL, PROFILE_SUMMARY, BEST_ANGLE, SENTIMENT. tone only used for EMAIL_DRAFT. */
    record EnrichContactCommand(String type, String tone) {}

    record EnrichmentResult(
        UUID enrichmentId, String type, String content, String modelUsed,
        int promptTokens, int completionTokens, double confidenceScore
    ) {}
}
