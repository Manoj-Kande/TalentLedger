package com.talentledger.infrastructure.persistence.entity;

/**
 * MFA method types stored in the {@code users.mfa_type} column.
 */
public enum MfaType {
    TOTP,
    SMS,
    EMAIL
}
