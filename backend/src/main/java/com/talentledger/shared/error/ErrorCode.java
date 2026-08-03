package com.talentledger.shared.error;

/**
 * Error code catalog (40+ codes).
 * Ranges:
 *   1000-1099: Auth
 *   2000-2099: Quota
 *   3000-3099: Dump
 *   4000-4099: Contact
 *   9000-9099: System
 */
public enum ErrorCode {

    // ── Auth (1000-1099) ──────────────────────────────────
    INVALID_CREDENTIALS("1000", "Invalid email or password"),
    USER_NOT_FOUND("1001", "User not found"),
    ACCOUNT_LOCKED("1002", "Account is temporarily locked"),
    ACCOUNT_SUSPENDED("1003", "Account has been suspended"),
    ACCOUNT_BANNED("1004", "Account has been permanently banned"),
    SESSION_EXPIRED("1005", "Session has expired"),
    SESSION_REVOKED("1006", "Session has been revoked"),
    SESSION_NOT_FOUND("1007", "Session not found"),
    JWT_INVALID("1008", "Invalid authentication token"),
    JWT_EXPIRED("1009", "Authentication token has expired"),
    MFA_REQUIRED("1010", "Multi-factor authentication required"),
    MFA_INVALID_CODE("1011", "Invalid MFA code"),
    MFA_NOT_ENABLED("1012", "MFA is not enabled for this account"),
    MFA_ALREADY_ENABLED("1013", "MFA is already enabled"),
    PASSWORD_TOO_WEAK("1014", "Password does not meet requirements"),
    PASSWORD_REUSED("1015", "Password was recently used"),
    RESET_TOKEN_INVALID("1016", "Password reset token is invalid or expired"),
    RESET_TOKEN_EXPIRED("1017", "Password reset token has expired"),
    EMAIL_ALREADY_VERIFIED("1018", "Email is already verified"),
    EMAIL_NOT_VERIFIED("1019", "Email address has not been verified"),
    EMAIL_ALREADY_EXISTS("1020", "Email address is already registered"),
    CLERK_JWT_INVALID("1021", "Clerk JWT validation failed"),
    IMPERSONATION_SESSION_EXPIRED("1022", "Impersonation session has expired"),

    // ── Quota (2000-2099) ─────────────────────────────────
    UPLOAD_LIMIT_EXCEEDED("2000", "Monthly upload limit reached"),
    CONTACT_LIMIT_EXCEEDED("2001", "Contact storage limit reached"),
    STORAGE_LIMIT_EXCEEDED("2002", "Storage space limit reached"),
    AI_CREDITS_EXCEEDED("2003", "AI credits limit reached"),
    ACTIVE_DUMP_LIMIT_EXCEEDED("2004", "Active dump limit reached"),
    DEMO_EXPIRED("2005", "Demo dump has expired"),
    FILE_TOO_LARGE("2006", "File exceeds size limit for your plan"),
    EXPORT_LIMIT_EXCEEDED("2007", "Export row limit reached"),

    // ── Dump (3000-3099) ─────────────────────────────────
    DUMP_NOT_FOUND("3000", "Dump not found"),
    DUMP_PARSE_FAILED("3001", "Dump parsing failed"),
    DUMP_FILE_TOO_LARGE("3002", "File exceeds maximum allowed size"),
    DUMP_INVALID_TYPE("3003", "Unsupported file type"),
    DUMP_NOT_OWNER("3004", "You do not own this dump"),
    DUMP_ALREADY_PROCESSING("3005", "Dump is already being processed"),
    DUMP_IDEMPOTENT("3006", "Identical file already uploaded"),

    // ── Contact (4000-4099) ───────────────────────────────
    CONTACT_NOT_FOUND("4000", "Contact not found"),
    CONTACT_NOT_OWNER("4001", "You do not own this contact"),
    CONTACT_DUPLICATE_EMAIL("4002", "A contact with this email already exists"),
    CONTACT_VALIDATION_FAILED("4003", "Contact data validation failed"),
    CONTACT_BULK_LIMIT("4004", "Bulk operation exceeds maximum of 500"),

    // ── System (9000-9099) ────────────────────────────────
    MAINTENANCE_MODE("9000", "System is under maintenance"),
    RATE_LIMITED("9001", "Too many requests. Try again later."),
    INTERNAL_ERROR("9002", "An internal error occurred"),
    INVALID_REQUEST("9003", "Invalid request"),
    FEATURE_DISABLED("9004", "This feature is currently disabled"),
    PROVIDER_UNAVAILABLE("9005", "External service unavailable");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
