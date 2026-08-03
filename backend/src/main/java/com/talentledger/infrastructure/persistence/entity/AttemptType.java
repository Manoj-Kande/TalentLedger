package com.talentledger.infrastructure.persistence.entity;

/**
 * Login attempt types stored in the {@code login_history.attempt_type} column.
 */
public enum AttemptType {
    OAUTH_GOOGLE,
    OAUTH_GITHUB,
    PASSWORD,
    API_KEY,
    IMPERSONATION
}
