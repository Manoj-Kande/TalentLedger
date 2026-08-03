package com.talentledger.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateSavedListRequest(
    @NotBlank String name,
    String description
) {}
