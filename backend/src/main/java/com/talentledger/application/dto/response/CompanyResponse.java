package com.talentledger.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
    UUID id,
    String normalizedName,
    String displayName,
    String category,
    String industry,
    String sizeRange,
    String headquarters,
    String domain,
    String logoUrl,
    int contactCount,
    Instant createdAt
) {}
