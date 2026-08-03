package com.talentledger.domain.contact;

import com.talentledger.domain.shared.BusinessRule;

/**
 * Immutable value object representing the canonical form of an email address
 * used for deduplication comparisons.
 *
 * <p>Applies Gmail-specific normalization rules for {@code @gmail.com} and
 * {@code @googlemail.com} addresses:
 * <ul>
 *   <li>Strips all {@code '.'} characters from the local part</li>
 *   <li>Strips the {@code '+'} tag and everything after it from the local part</li>
 * </ul>
 *
 * <p>For all other domains, the value is simply lowercased and trimmed.
 *
 * <p>Pure Java — zero framework dependency.
 */
public final class NormalizedEmail {

    private static final String GMAIL_DOMAIN = "gmail.com";
    private static final String GOOGLEMAIL_DOMAIN = "googlemail.com";

    private final String value;

    private NormalizedEmail(String value) {
        this.value = value;
    }

    /**
     * Factory method: produce a canonical email string for dedup.
     *
     * @param email any email string (raw or pre-normalized)
     * @return an immutable NormalizedEmail
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException
     *     if the email is null, blank, or lacks an '@'
     */
    public static NormalizedEmail of(String email) {
        BusinessRule.notNull(email, "Email for normalization");

        String normalized = email.trim().toLowerCase();
        BusinessRule.ensure(!normalized.isEmpty(), "Email must not be blank");
        BusinessRule.ensure(
                normalized.contains("@"), "Email must contain '@'");

        int atIndex = normalized.indexOf('@');
        String localPart = normalized.substring(0, atIndex);
        String domainPart = normalized.substring(atIndex + 1);

        // Apply Gmail normalization rules for @gmail.com and @googlemail.com
        if (GMAIL_DOMAIN.equals(domainPart) || GOOGLEMAIL_DOMAIN.equals(domainPart)) {
            localPart = normalizeGmailLocalPart(localPart);
            // Canonical domain is always gmail.com
            domainPart = GMAIL_DOMAIN;
        }

        return new NormalizedEmail(localPart + "@" + domainPart);
    }

    /**
     * Gmail-specific local-part normalization:
     * 1. Remove all '.' characters
     * 2. Remove '+' tag and everything after it
     */
    private static String normalizeGmailLocalPart(String localPart) {
        // Strip '+' tag and everything after
        int plusIndex = localPart.indexOf('+');
        if (plusIndex >= 0) {
            localPart = localPart.substring(0, plusIndex);
        }
        // Strip all '.' characters
        return localPart.replace(".", "");
    }

    /** The canonical email string for dedup comparison. */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NormalizedEmail that = (NormalizedEmail) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
