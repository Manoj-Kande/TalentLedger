package com.talentledger.domain.contact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Domain service encapsulating pure business logic that does not naturally
 * belong on a single entity.
 *
 * <p>Pure Java — zero framework annotations. Stateless and side-effect-free.
 */
public final class ContactDomainService {

    private ContactDomainService() {
        // Static utility — prevent instantiation
    }

    // ── Weights for verification score heuristic ───────────

    private static final int WEIGHT_EMAIL = 20;
    private static final int WEIGHT_NAME = 15;
    private static final int WEIGHT_PHONE = 15;
    private static final int WEIGHT_LINKEDIN = 20;
    private static final int WEIGHT_TITLE = 10;
    private static final int WEIGHT_COMPANY = 10;
    private static final int WEIGHT_LOCATION = 5;
    private static final int WEIGHT_SOURCE_URL = 5;

    // ── Public API ─────────────────────────────────────────

    /**
     * Remove duplicates from a list of contacts by normalized email.
     *
     * <p>When duplicates are found, the first occurrence is kept and all
     * subsequent contacts sharing the same normalized email are dropped.
     * Order of non-duplicate contacts is preserved.
     *
     * @param contacts the input list (may be empty, must not be null)
     * @return a new list with duplicates removed
     */
    public static List<Contact> deduplicateEmails(List<Contact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> seen = new HashSet<>();
        List<Contact> unique = new ArrayList<>();

        for (Contact contact : contacts) {
            String normalized = contact.getNormalizedEmail().getValue();
            if (seen.add(normalized)) {
                unique.add(contact);
            }
        }

        return unique;
    }

    /**
     * Compute a heuristic verification score (0–100) for a contact based on
     * data completeness. The more populated fields a contact has, the higher
     * the score.
     *
     * <p>This is a lightweight heuristic — not a real email verification.
     * Actual email deliverability checks would be handled by an external
     * service integration.
     *
     * @param contact the contact to score (must not be null)
     * @return an integer between 0 and 100
     */
    public static int computeVerificationScore(Contact contact) {
        if (contact == null) {
            return 0;
        }

        int score = 0;

        // Email is always present (invariant), so give the base weight
        score += WEIGHT_EMAIL;

        // Name is always present (invariant), so give the base weight
        score += WEIGHT_NAME;

        if (contact.hasPhone()) {
            score += WEIGHT_PHONE;
        }

        if (contact.hasLinkedIn()) {
            score += WEIGHT_LINKEDIN;
        }

        if (contact.getTitle() != null && !contact.getTitle().isBlank()) {
            score += WEIGHT_TITLE;
        }

        if (contact.getCompanyId() != null) {
            score += WEIGHT_COMPANY;
        }

        if (contact.getLocation() != null && !contact.getLocation().isBlank()) {
            score += WEIGHT_LOCATION;
        }

        if (contact.getSourceUrl() != null && !contact.getSourceUrl().isBlank()) {
            score += WEIGHT_SOURCE_URL;
        }

        return Math.min(100, Math.max(0, score));
    }

    /**
     * Extract the domain portion from an Email value object.
     *
     * <p>Convenience method that delegates to {@link Email#getDomain()}.
     *
     * @param email the email to extract from (must not be null)
     * @return the domain string (e.g. "gmail.com")
     */
    public static String extractDomain(Email email) {
        if (email == null) {
            return "";
        }
        return email.getDomain();
    }

    /**
     * Batch-score a list of contacts, returning a map of contact id → score.
     *
     * @param contacts the contacts to score
     * @return a map keyed by contact UUID
     */
    public static Map<java.util.UUID, Integer> batchScore(List<Contact> contacts) {
        Map<java.util.UUID, Integer> scores = new LinkedHashMap<>();
        if (contacts == null) {
            return scores;
        }
        for (Contact contact : contacts) {
            scores.put(contact.getId(), computeVerificationScore(contact));
        }
        return scores;
    }
}
