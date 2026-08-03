package com.talentledger.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AuthResponse(
    String sessionToken,
    UUID userId,
    String email,
    String name,
    String role,
    String plan,
    boolean emailVerified,
    boolean mfaRequired,
    boolean isGuest,
    Instant expiresAt
) {}
