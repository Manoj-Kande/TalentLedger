package com.talentledger.domain.auth;

import com.talentledger.domain.shared.Result;

/**
 * Domain service enforcing password policies.
 *
 * <p>Validates raw passwords against minimum-strength requirements
 * and detects when stored bcrypt hashes need rehashing to a higher cost.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 12;
    private static final int CURRENT_BCRYPT_COST = 12;

    private PasswordPolicy() {
        // static utility
    }

    /**
     * Validate a raw password against policy requirements.
     *
     * <p>Rules:
     * <ul>
     *   <li>Minimum 12 characters</li>
     *   <li>At least one uppercase letter</li>
     *   <li>At least one lowercase letter</li>
     *   <li>At least one digit</li>
     * </ul>
     *
     * @param rawPassword the plaintext password to validate
     * @return success if valid, failure with a descriptive error message otherwise
     */
    public static Result<Boolean, String> validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return Result.failure("Password must not be blank");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            return Result.failure("Password must be at least " + MIN_LENGTH + " characters");
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        for (int i = 0; i < rawPassword.length(); i++) {
            char c = rawPassword.charAt(i);
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasUpper) {
            return Result.failure("Password must contain at least one uppercase letter");
        }
        if (!hasLower) {
            return Result.failure("Password must contain at least one lowercase letter");
        }
        if (!hasDigit) {
            return Result.failure("Password must contain at least one digit");
        }
        return Result.success(true);
    }

    /**
     * Check whether a stored bcrypt hash uses the current cost factor.
     *
     * <p>Bcrypt hashes have the format {@code $2b$cost$...}. This method extracts
     * the cost and compares it against {@value #CURRENT_BCRYPT_COST}.
     *
     * @param storedHash a bcrypt hash string
     * @return true if the hash cost is lower than the current policy cost
     */
    public static boolean needsRehash(String storedHash) {
        if (storedHash == null || storedHash.length() < 7) {
            return true;
        }
        // bcrypt format: $2b$12$... or $2a$12$...
        try {
            int costStart = 4; // after "$2b$" or "$2a$"
            int dollarEnd = storedHash.indexOf('$', costStart);
            if (dollarEnd < 0) {
                return true;
            }
            String costStr = storedHash.substring(costStart, dollarEnd);
            int storedCost = Integer.parseInt(costStr);
            return storedCost < CURRENT_BCRYPT_COST;
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            return true;
        }
    }
}
