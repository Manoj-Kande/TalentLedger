package com.talentledger.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCampaignRequest(
    @NotBlank String name,
    String description
) {}
