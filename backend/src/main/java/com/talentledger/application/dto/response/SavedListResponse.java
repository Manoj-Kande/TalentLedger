package com.talentledger.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SavedListResponse(
    UUID id,
    String name,
    String description,
    boolean isDynamic,
    int contactCount,
    Instant createdAt,
    Instant updatedAt
) {}
