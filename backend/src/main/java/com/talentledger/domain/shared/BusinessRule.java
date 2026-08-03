package com.talentledger.domain.shared;

/**
 * A declarative business rule that must hold true within the domain.
 *
 * <p>Business rules are checked in entity constructors and factory methods.
 * Violations throw {@link BusinessRuleViolationException}.
 *
 * <p>Usage:
 * <pre>
 *   BusinessRule.ensure(name != null && !name.isBlank(), "Name must not be empty");
 *   BusinessRule.ensure(email.length() <= 255, "Email exceeds max length");
 * </pre>
 */
public final class BusinessRule {

    private BusinessRule() {} // static utility

    /**
     * Check that the given condition is true. If not, throw with the given message.
     *
     * @param condition the invariant that must hold
     * @param message   human-readable violation message
     * @throws BusinessRuleViolationException if condition is false
     */
    public static void ensure(boolean condition, String message) {
        if (!condition) {
            throw new BusinessRuleViolationException(message);
        }
    }

    /**
     * Check that the given condition is true. If not, throw with a formatted message.
     *
     * @param condition  the invariant that must hold
     * @param format     printf-style format string
     * @param args       format arguments
     * @throws BusinessRuleViolationException if condition is false
     */
    public static void ensure(boolean condition, String format, Object... args) {
        if (!condition) {
            throw new BusinessRuleViolationException(String.format(format, args));
        }
    }

    /**
     * Assert that the given value is not null.
     *
     * @param value   the value to check
     * @param fieldName the field name for error message
     * @param <T>     the value type
     * @return the non-null value
     * @throws BusinessRuleViolationException if value is null
     */
    public static <T> T notNull(T value, String fieldName) {
        if (value == null) {
            throw new BusinessRuleViolationException(fieldName + " must not be null");
        }
        return value;
    }
}
