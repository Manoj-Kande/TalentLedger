package com.talentledger.shared.constants;

/**
 * Security constants used across the application.
 */
public final class SecurityConstants {

    private SecurityConstants() {}

    /** Session token header name. */
    public static final String SESSION_TOKEN_HEADER = "X-Session-Token";

    /** API key header name. */
    public static final String API_KEY_HEADER = "X-API-Key";

    /** Request ID header name. */
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    /** Clerk webhook signature header. */
    public static final String CLERK_SIGNATURE_HEADER = "svix-id";

    /** Bearer token prefix (not used — we use opaque tokens, but kept for future). */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Session token entropy in bytes. */
    public static final int SESSION_TOKEN_BYTES = 32;

    /** Default session TTL in hours. */
    public static final int DEFAULT_SESSION_TTL_HOURS = 24;

    /** Max concurrent sessions by plan. */
    public static final int FREE_MAX_SESSIONS = 3;
    public static final int PRO_MAX_SESSIONS = 10;

    /** bcrypt rounds. */
    public static final int BCRYPT_ROUNDS = 12;

    /** MFA clock skew tolerance (time steps). */
    public static final int MFA_CLOCK_SKEW_STEPS = 1;

    /** MFA code length (digits). */
    public static final int MFA_CODE_LENGTH = 6;

    /** Backup codes count. */
    public static final int BACKUP_CODES_COUNT = 10;
}
