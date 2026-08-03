package com.talentledger.domain.user;

/**
 * The current status of a user account.
 *
 * <p>Controls whether the user can log in and access features.
 * Pure Java enum — zero framework annotations.
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    BANNED,
    PENDING_DELETION
}
