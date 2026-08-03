package com.talentledger.domain.contact;

import com.talentledger.domain.shared.BusinessRule;

/**
 * Immutable value object representing a validated email address.
 *
 * <p>Holds the raw value alongside a normalized (lowercased, trimmed) form
 * and the extracted domain portion. Does NOT perform Gmail-specific
 * normalization — see {@link NormalizedEmail} for canonical dedup form.
 *
 * <p>Pure Java — zero framework dependency.
 */
public final class Email {

    /** Maximum allowed length for an email string. */
    private static final int MAX_LENGTH = 255;

    private final String value;
    private final String normalized;
    private final String domain;

    private Email(String value, String normalized, String domain) {
        this.value = value;
        this.normalized = normalized;
        this.domain = domain;
    }

    /**
     * Factory method: validate and construct an Email.
     *
     * @param rawEmail the raw email string supplied by the caller
     * @return an immutable Email value object
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException
     *     if the email is null, blank, too long, or has an invalid format
     */
    public static Email of(String rawEmail) {
        BusinessRule.notNull(rawEmail, "Email");

        String trimmed = rawEmail.trim();
        BusinessRule.ensure(!trimmed.isEmpty(), "Email must not be blank");
        BusinessRule.ensure(
                trimmed.length() <= MAX_LENGTH,
                "Email must not exceed %d characters", MAX_LENGTH);

        String lowercased = trimmed.toLowerCase();

        // Basic format: must contain exactly one '@'
        int atIndex = lowercased.indexOf('@');
        BusinessRule.ensure(atIndex > 0, "Email must contain '@' with a local part before it");

        // No second '@'
        BusinessRule.ensure(
                lowercased.indexOf('@', atIndex + 1) == -1,
                "Email must contain exactly one '@'");

        String domainPart = lowercased.substring(atIndex + 1);

        // Domain must contain at least one '.'
        BusinessRule.ensure(
                domainPart.contains("."),
                "Email domain must contain at least one '.' after '@'");

        // Domain must not end with '.'
        BusinessRule.ensure(
                !domainPart.endsWith("."),
                "Email domain must not end with '.'");

        // Domain part between '@' and first '.' must not be empty
        int dotIndex = domainPart.indexOf('.');
        BusinessRule.ensure(
                dotIndex > 0,
                "Email domain part before '.' must not be empty");

        return new Email(trimmed, lowercased, domainPart);
    }

    /** The raw email as provided (trimmed but original case). */
    public String getValue() {
        return value;
    }

    /** Lowercase, trimmed form. */
    public String getNormalized() {
        return normalized;
    }

    /** The domain portion (everything after '@'), lowercased. */
    public String getDomain() {
        return domain;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return normalized.equals(email.normalized);
    }

    @Override
    public int hashCode() {
        return normalized.hashCode();
    }
}
