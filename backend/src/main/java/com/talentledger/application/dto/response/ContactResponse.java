package com.talentledger.application.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ContactResponse(
    UUID id,
    String name,
    String email,
    String normalizedEmail,
    String phone,
    String linkedinUrl,
    String secondaryEmail,
    String title,
    String department,
    String seniorityLevel,
    String location,
    String timezone,
    String language,
    String domain,
    int verificationScore,
    String source,
    UUID primaryDumpId,
    UUID companyId,
    String companyName,
    String notes,
    List<String> tags,
    Map<String, Object> customFields,
    Map<String, Object> aiEnrichment,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
