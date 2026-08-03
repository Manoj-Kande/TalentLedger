package com.talentledger.application.dto.request;

public record UpdateCampaignRequest(
    String name,
    String description,
    String scheduledAt
) {}
