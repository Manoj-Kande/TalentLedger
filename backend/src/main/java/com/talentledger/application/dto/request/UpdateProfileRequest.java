package com.talentledger.application.dto.request;

public record UpdateProfileRequest(
    String name,
    String timezone,
    String locale,
    String avatarUrl
) {}
