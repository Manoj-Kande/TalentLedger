package com.talentledger.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CampaignResponse(
    UUID id,
    String name,
    String description,
    UUID templateId,
    String status,
    int totalContacts,
    int sentCount,
    int replyCount,
    int bounceCount,
    Instant scheduledAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt
) {}
