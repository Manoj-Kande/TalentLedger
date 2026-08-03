package com.talentledger.application.port.inbound;

import com.talentledger.application.dto.response.ContactResponse;
import com.talentledger.domain.dump.DataDump;
import com.talentledger.domain.dump.DumpStatus;
import com.talentledger.domain.shared.Result;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port — Data Dump use cases.
 * Implemented by DumpService in the application layer.
 */
public interface DumpUseCase {

    /** Upload a new file (multipart). Returns the created dump. */
    Result<DataDump, String> uploadDump(UUID userId, UploadDumpCommand command);

    /** Get dump detail with ownership check. */
    Result<DataDump, String> getDump(UUID dumpId, UUID userId);

    /** List user's dumps with sort, search, and cursor pagination. */
    Result<DumpListResult, String> listDumps(UUID userId, DumpListQuery query);

    /** Update dump metadata (rename, pin, archive). */
    Result<DataDump, String> updateDump(UUID dumpId, UUID userId, UpdateDumpCommand command);

    /** Soft-delete a dump and cascade. */
    Result<Void, String> deleteDump(UUID dumpId, UUID userId);

    /** Re-run parsing for a FAILED dump using the originally-uploaded file (still on disk). */
    Result<Void, String> retryDump(UUID dumpId, UUID userId);

    /**
     * Item #6: every upload starts as an unconfirmed preview (isPersisted=false,
     * auto-expires — see ScheduledJobs). This is the explicit "Save to Workspace"
     * action that runs the plan's dump/upload quota checks and makes it permanent.
     */
    Result<Void, String> confirmSaveDump(UUID dumpId, UUID userId);

    /** Get contacts within a specific dump. */
    Result<DumpContactsResult, String> getDumpContacts(UUID dumpId, UUID userId, CursorQuery cursor);

    /** Get parse errors for a dump. */
    Result<ParseErrorsResult, String> getDumpErrors(UUID dumpId, UUID userId);

    /** Export dump contacts as CSV/JSON stream. */
    Result<ExportStream, String> exportDump(UUID dumpId, UUID userId, String format);

    // ── Inner types ──────────────────────────────────────

    record UploadDumpCommand(
        String originalFilename,
        String fileType,
        long fileSizeBytes,
        String fileHash,
        String description,
        List<String> tags,
        java.io.InputStream fileStream
    ) {}

    record UpdateDumpCommand(
        String name,
        Boolean pinned,
        Boolean archived
    ) {}

    record DumpListQuery(
        String search,
        String sortBy,
        Boolean archivedOnly,
        String nextCursor,
        int pageSize
    ) {}

    record DumpListResult(
        List<DataDump> dumps,
        String nextCursor,
        boolean hasMore
    ) {}

    // Field names (items/nextCursor/hasMore) intentionally match the
    // frontend's PaginatedResponse<T> shape. This used to return bare
    // contactIds, which the frontend's Recent Contacts / dump-contacts
    // pages had no way to render into a Contact card (name/email/etc).
    record DumpContactsResult(
        List<ContactResponse> items,
        String nextCursor,
        boolean hasMore
    ) {}

    record ParseErrorsResult(
        List<java.util.Map<String, Object>> errors,
        int totalErrors
    ) {}

    record CursorQuery(String nextCursor, int pageSize) {}
    record ExportStream(String filename, String mediaType, java.io.InputStream stream) {}
}
