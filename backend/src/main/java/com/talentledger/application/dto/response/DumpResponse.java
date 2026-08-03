package com.talentledger.application.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DumpResponse(
    UUID id,
    String name,
    String description,
    List<String> tags,
    String originalFilename,
    String fileType,
    long fileSizeBytes,
    String status,
    int totalRows,
    int parsedContactsCount,
    int liveContactsCount,
    int duplicateWithinDumpCount,
    int crossDumpDuplicateCount,
    int errorCount,
    Long parseDurationMs,
    boolean isPinned,
    boolean isArchived,
    boolean isPersisted,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt,
    Instant expiresAt
) {}
