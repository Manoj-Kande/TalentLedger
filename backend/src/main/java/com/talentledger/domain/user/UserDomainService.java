package com.talentledger.domain.user;

import java.time.Duration;
import java.util.Optional;

/**
 * Domain service for User-related pure business logic.
 *
 * <p>Encapsulates rules that do not naturally belong on a single entity
 * (e.g. password policy, lockout thresholds, quota initialisation).
 * Pure Java — zero framework annotations. Stateless.
 */
public final class UserDomainService {

    private UserDomainService() {}

    // ── Password Policy ────────────────────────────────

    private static final int MIN_PASSWORD_LENGTH = 12;

    /**
     * Validate that the given password meets compliance requirements.
     *
     * <p>Rules:
     * <ul>
     *   <li>At least 12 characters</li>
     *   <li>At least one uppercase letter</li>
     *   <li>At least one lowercase letter</li>
     *   <li>At least one digit</li>
     *   <li>At least one special character (non-alphanumeric)</li>
     * </ul>
     *
     * @param password the password to validate (must not be null)
     * @return true if the password meets all requirements
     */
    public static boolean validatePasswordCompliance(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    // ── Account Lockout ──────────────────────────────

    private static final int LOCKOUT_THRESHOLD_1 = 5;
    private static final int LOCKOUT_THRESHOLD_2 = 10;
    private static final int SUSPEND_THRESHOLD = 20;

    /**
     * Determine the lockout duration for the given number of failed login attempts.
     *
     * <p>Thresholds:
     * <ul>
     *   <li>5+ failed attempts → 15 minutes</li>
     *   <li>10+ failed attempts → 60 minutes</li>
     *   <li>20+ failed attempts → should suspend (handled separately) </li>
     * </ul>
     *
     * @param failedAttempts the number of consecutive failed attempts
     * @return the lockout duration, or empty if no lockout applies
     */
    public static Optional<Duration> isAccountLockoutThreshold(int failedAttempts) {
        if (failedAttempts >= SUSPEND_THRESHOLD) {
            return Optional.empty();
        }
        if (failedAttempts >= LOCKOUT_THRESHOLD_2) {
            return Optional.of(Duration.ofMinutes(60));
        }
        if (failedAttempts >= LOCKOUT_THRESHOLD_1) {
            return Optional.of(Duration.ofMinutes(15));
        }
        return Optional.empty();
    }

    /**
     * Check whether the given number of failed attempts warrants suspension.
     *
     * @param failedAttempts the number of consecutive failed attempts
     * @return true if the account should be suspended (20+ failed attempts)
     */
    public static boolean shouldSuspendAccount(int failedAttempts) {
        return failedAttempts >= SUSPEND_THRESHOLD;
    }

    // ── Quota Factory ────────────────────────────────

    /**
     * Create a new UserQuota with plan-specific default limits.
     *
     * @param userId the user's UUID
     * @param plan   the subscription plan
     * @return a new UserQuota with defaults for the given plan
     */
    public static UserQuota initializeQuotaForPlan(java.util.UUID userId, UserPlan plan) {
        return UserQuota.Builder.forUser(userId, plan).build();
    }
}