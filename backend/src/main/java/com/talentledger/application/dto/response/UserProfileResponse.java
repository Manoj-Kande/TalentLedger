package com.talentledger.application.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    String name,
    String avatarUrl,
    String role,
    String plan,
    String status,
    boolean emailVerified,
    boolean mfaEnabled,
    boolean onboardingCompleted,
    boolean isGuest,
    QuotaResponse quotas,
    Instant createdAt
) {}
