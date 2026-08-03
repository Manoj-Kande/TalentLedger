package com.talentledger.domain.dump;

import com.talentledger.domain.shared.AggregateRoot;
import com.talentledger.domain.shared.BusinessRule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DataDump aggregate root — the central entity of the Dump bounded context.
 *
 * <p>Represents a single file uploaded by a user containing contact data.
 * Tracks the full lifecycle from upload through parsing to completion.
 *
 * <p>Extends {@link AggregateRoot} to inherit identity, domain-event
 * collection, and soft-delete tracking.
 *
 * <p>Pure Java — zero framework annotations. Use the static factory method
 * {@link #create} to instantiate.
 */
public final class DataDump extends AggregateRoot<UUID> {

    // ── Constants ───────────────────────────────────────────

    private static final int MAX_FILENAME_LENGTH = 500;
    private static final String DEFAULT_STORAGE_PROVIDER = "R2";

    // ── Fields ──────────────────────────────────────────────

    private UUID userId;
    private String name;
    private String description;
    private List<String> tags;
    private String originalFilename;
    private FileType fileType;
    private long fileSizeBytes;
    private String fileHash;
    private ColumnMapping columnMapping;
    private DumpStatus status;
    private int totalRows;
    private int parsedContactsCount;
    private int liveContactsCount;
    private int duplicateWithinDumpCount;
    private int crossDumpDuplicateCount;
    private int errorCount;
    private List<Map<String, Object>> parseErrors;
    private int parseDurationMs;
    private boolean isPinned;
    private boolean isArchived;
    private boolean isPersisted;
    private String storageProvider;
    private String originalFileKey;
    private String parsedSnapshotKey;
    private Instant originalFileDeletedAt;
    private Instant expiresAt;
    private Instant completedAt;

    // ── Private Constructor ─────────────────────────────────

    private DataDump(UUID id) {
        super(id);
        this.tags = new ArrayList<>();
        this.parseErrors = new ArrayList<>();
        this.status = DumpStatus.PENDING;
        this.totalRows = 0;
        this.parsedContactsCount = 0;
        this.liveContactsCount = 0;
        this.duplicateWithinDumpCount = 0;
        this.crossDumpDuplicateCount = 0;
        this.errorCount = 0;
        this.parseDurationMs = 0;
        this.isPinned = false;
        this.isArchived = false;
        this.isPersisted = false;
        this.storageProvider = DEFAULT_STORAGE_PROVIDER;
    }

    // ── Factory ────────────────────────────────────────────

    /**
     * Create a new DataDump aggregate.
     *
     * <p>Business rules enforced:
     * <ul>
     *   <li>userId must not be null</li>
     *   <li>name must not be null or blank</li>
     *   <li>originalFilename must not be null and max 500 chars</li>
     *   <li>fileType must not be null</li>
     *   <li>fileSizeBytes must be >= 0</li>
     * </ul>
     *
     * @param userId            the owning user's id (must not be null)
     * @param name              human-readable name for this dump (must not be blank)
     * @param originalFilename  the uploaded file's original name (max 500 chars)
     * @param fileType          the type of the uploaded file
     * @param fileSizeBytes     the size of the uploaded file in bytes (>= 0)
     * @return a fully initialised DataDump in PENDING status
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException
     *     on any rule violation
     */
    public static DataDump create(UUID userId,
                                  String name,
                                  String originalFilename,
                                  FileType fileType,
                                  long fileSizeBytes) {
        BusinessRule.notNull(userId, "User ID");
        BusinessRule.notNull(name, "Name");
        BusinessRule.ensure(!name.isBlank(), "Name must not be blank");
        BusinessRule.notNull(originalFilename, "Original filename");
        BusinessRule.ensure(originalFilename.length() <= MAX_FILENAME_LENGTH,
                "Original filename must not exceed %d characters", MAX_FILENAME_LENGTH);
        BusinessRule.notNull(fileType, "File type");
        BusinessRule.ensure(fileSizeBytes >= 0, "File size must be >= 0");

        UUID dumpId = UUID.randomUUID();
        DataDump dump = new DataDump(dumpId);

        dump.userId = userId;
        dump.name = name;
        dump.originalFilename = originalFilename;
        dump.fileType = fileType;
        dump.fileSizeBytes = fileSizeBytes;

        Instant now = Instant.now();
        dump.createdAt = now;
        dump.updatedAt = now;

        return dump;
    }

    // ── Lifecycle ───────────────────────────────────────────

    /**
     * Transition to PARSING status.
     *
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException
     *     if not in PENDING status
     */
    public void startParsing() {
        BusinessRule.ensure(this.status == DumpStatus.PENDING,
                "Cannot start parsing: dump is in %s status", this.status);
        this.status = DumpStatus.PARSING;
        this.updatedAt = Instant.now();
    }

    /**
     * Transition to COMPLETED status, recording all parse results.
     *
     * <p>Registers a {@link DumpImportedEvent} for downstream processing.
     *
     * @param result the parse result (must not be null)
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException
     *     if not in PARSING status
     */
    public void completeParsing(ParseResult result) {
        BusinessRule.notNull(result, "Parse result");
        BusinessRule.ensure(this.status == DumpStatus.PARSING,
                "Cannot complete parsing: dump is in %s status", this.status);

        this.status = DumpStatus.COMPLETED;
        this.totalRows = result.getTotalRows();
        this.parsedContactsCount = result.getParsedContactsCount();
        this.duplicateWithinDumpCount = result.getDuplicateWithinDumpCount();
        this.crossDumpDuplicateCount = result.getCrossDumpDuplicateCount();
        this.errorCount = result.getErrorCount();
        this.parseDurationMs = (int) result.getParseDurationMs();
        this.liveContactsCount = this.parsedContactsCount - this.duplicateWithinDumpCount;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;

        // Store parse errors as JSONB-compatible maps
        this.parseErrors = new ArrayList<>();
        for (String error : result.getParseErrors()) {
            Map<String, Object> errorEntry = new HashMap<>();
            errorEntry.put("message", error);
            this.parseErrors.add(errorEntry);
        }

        registerEvent(new DumpImportedEvent(this.id, this.userId, result));
    }

    /**
     * Transition to FAILED status.
     *
     * @param errorMessage human-readable description of the failure
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException
     *     if errorMessage is blank
     */
    public void markFailed(String errorMessage) {
        BusinessRule.notNull(errorMessage, "Error message");
        BusinessRule.ensure(!errorMessage.isBlank(), "Error message must not be blank");
        this.status = DumpStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    /**
     * Transition to CANCELLED status.
     */
    public void markCancelled() {
        this.status = DumpStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    /**
     * Transition to EXPIRED status.
     *
     * <p>Typically called by a scheduled job cleaning up free-tier dumps
     * that have passed their TTL.
     */
    public void markExpired() {
        this.status = DumpStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    // ── Pin / Unpin ─────────────────────────────────────────

    /** Pin this dump so it appears at the top of lists. */
    public void pin() {
        this.isPinned = true;
        this.updatedAt = Instant.now();
    }

    /** Unpin this dump. */
    public void unpin() {
        this.isPinned = false;
        this.updatedAt = Instant.now();
    }

    // ── Archive / Unarchive ─────────────────────────────────

    /** Archive this dump — hides it from default views. */
    public void archive() {
        this.isArchived = true;
        this.updatedAt = Instant.now();
    }

    /** Unarchive this dump — restores it to default views. */
    public void unarchive() {
        this.isArchived = false;
        this.updatedAt = Instant.now();
    }

    // ── Rename ──────────────────────────────────────────────

    /**
     * Rename this dump.
     *
     * @param newName the new name (must not be null or blank)
     */
    public void rename(String newName) {
        BusinessRule.notNull(newName, "New name");
        BusinessRule.ensure(!newName.isBlank(), "Name must not be blank");
        this.name = newName;
        this.updatedAt = Instant.now();
    }

    // ── Column Mapping ──────────────────────────────────────

    /**
     * Set the column mapping after header detection.
     *
     * @param mapping the detected column mapping (must not be null)
     */
    public void setColumnMapping(ColumnMapping mapping) {
        BusinessRule.notNull(mapping, "Column mapping");
        this.columnMapping = mapping;
        this.updatedAt = Instant.now();
    }

    // ── Queries ─────────────────────────────────────────────

    /**
     * Check whether this free-tier dump has expired.
     *
     * <p>A dump is considered expired when:
     * <ul>
     *   <li>it has not been persisted (isPersisted == false)</li>
     *   <li>an expiry time is set (expiresAt != null)</li>
     *   <li>the expiry time is in the past</li>
     * </ul>
     *
     * @return true if this dump is a free-tier expired dump
     */
    public boolean isFreeTierExpired() {
        return !this.isPersisted
                && this.expiresAt != null
                && this.expiresAt.isBefore(Instant.now());
    }

    /**
     * Return the file size in bytes for storage calculations.
     *
     * @return fileSizeBytes
     */
    public long calculateStorageBytes() {
        return this.fileSizeBytes;
    }

    // ── Infrastructure Setters (for reconstitution) ─────────

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public void setParsedContactsCount(int parsedContactsCount) {
        this.parsedContactsCount = parsedContactsCount;
    }

    public void setLiveContactsCount(int liveContactsCount) {
        this.liveContactsCount = liveContactsCount;
    }

    public void setDuplicateWithinDumpCount(int duplicateWithinDumpCount) {
        this.duplicateWithinDumpCount = duplicateWithinDumpCount;
    }

    public void setCrossDumpDuplicateCount(int crossDumpDuplicateCount) {
        this.crossDumpDuplicateCount = crossDumpDuplicateCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public void setParseErrors(List<Map<String, Object>> parseErrors) {
        this.parseErrors = parseErrors != null ? new ArrayList<>(parseErrors) : new ArrayList<>();
    }

    public void setParseDurationMs(int parseDurationMs) {
        this.parseDurationMs = parseDurationMs;
    }

    public void setPersisted(boolean persisted) {
        this.isPersisted = persisted;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public void setOriginalFileKey(String originalFileKey) {
        this.originalFileKey = originalFileKey;
    }

    public void setParsedSnapshotKey(String parsedSnapshotKey) {
        this.parsedSnapshotKey = parsedSnapshotKey;
    }

    public void setOriginalFileDeletedAt(Instant originalFileDeletedAt) {
        this.originalFileDeletedAt = originalFileDeletedAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public void setStatus(DumpStatus status) {
        this.status = status;
    }

    // ── Getters ─────────────────────────────────────────────

    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getOriginalFilename() { return originalFilename; }
    public FileType getFileType() { return fileType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getFileHash() { return fileHash; }
    public ColumnMapping getColumnMapping() { return columnMapping; }
    public DumpStatus getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public int getParsedContactsCount() { return parsedContactsCount; }
    public int getLiveContactsCount() { return liveContactsCount; }
    public int getDuplicateWithinDumpCount() { return duplicateWithinDumpCount; }
    public int getCrossDumpDuplicateCount() { return crossDumpDuplicateCount; }
    public int getErrorCount() { return errorCount; }
    public int getParseDurationMs() { return parseDurationMs; }
    public boolean isPinned() { return isPinned; }
    public boolean isArchived() { return isArchived; }
    public boolean isPersisted() { return isPersisted; }
    public String getStorageProvider() { return storageProvider; }
    public String getOriginalFileKey() { return originalFileKey; }
    public String getParsedSnapshotKey() { return parsedSnapshotKey; }
    public Instant getOriginalFileDeletedAt() { return originalFileDeletedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCompletedAt() { return completedAt; }

    /** Returns an unmodifiable view of tags. */
    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    /** Returns an unmodifiable view of parse errors. */
    public List<Map<String, Object>> getParseErrors() {
        return Collections.unmodifiableList(parseErrors);
    }
}
