package com.talentledger.application.dto.request;

public record UpdateSavedListRequest(
    String name,
    String description,
    Boolean isDynamic
) {}
