package com.talentledger.domain.user;

/**
 * The subscription plan a user is enrolled in.
 *
 * <p>Determines quota limits and feature availability.
 * Pure Java enum — zero framework annotations.
 */
public enum UserPlan {
    FREE,
    PRO,
    TEAM,
    ENTERPRISE
}
