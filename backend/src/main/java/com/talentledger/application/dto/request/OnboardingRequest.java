package com.talentledger.application.dto.request;

import java.util.Map;

public record OnboardingRequest(
    boolean completed,
    Map<String, Object> profile
) {}
