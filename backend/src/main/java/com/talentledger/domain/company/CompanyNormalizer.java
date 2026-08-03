package com.talentledger.domain.company;

import java.util.Arrays;
import java.util.List;

/**
 * Domain service that normalizes company display names into a canonical form.
 *
 * <p>Strips common legal suffixes, lowercases, and trims whitespace so that
 * "Acme Inc.", "ACME INC", and "acme" all map to the same normalized name.
 *
 * <p>Static utility class — no state, no instantiation.
 */
public final class CompanyNormalizer {

    private CompanyNormalizer() {
        // static utility — prevent instantiation
    }

    /**
     * Ordered list of suffixes to strip. Longer suffixes are tried first
     * so that "Private Limited" is removed before "Limited" would match.
     */
    private static final List<String> SUFFIXES = Arrays.asList(
            "Private Limited",
            "Pvt. Ltd.",
            "Pvt Ltd",
            "Private Ltd",
            "Inc.",
            "Corporation",
            "Limited",
            "Ltd.",
            "LLC",
            "L.L.C.",
            "Inc",
            "Corp",
            "Co.",
            "Co",
            "Ltd",
            "GmbH",
            "AG",
            "S.A.",
            "S.A",
            "B.V.",
            "BV",
            "Pte",
            "Sdn Bhd"
    );

    /**
     * Normalize a company display name into a canonical, unique-safe string.
     *
     * <p>Steps:
     * <ol>
     *   <li>Trim leading/trailing whitespace</li>
     *   <li>Convert to lowercase</li>
     *   <li>Strip common legal suffixes (longer first to avoid partial matches)</li>
     *   <li>Collapse multiple whitespace characters into a single space</li>
     *   <li>Trim again</li>
     * </ol>
     *
     * @param name the raw display name
     * @return the normalized name, suitable for uniqueness checks
     */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }

        String result = name.trim().toLowerCase();

        for (String suffix : SUFFIXES) {
            String lowerSuffix = suffix.toLowerCase();
            if (result.endsWith(lowerSuffix)) {
                result = result.substring(0, result.length() - lowerSuffix.length());
            }
        }

        // Strip trailing commas, periods, dashes, underscores
        result = result.replaceAll("[\\s,\\-_.]+$", "");

        // Collapse multiple whitespace into single space
        result = result.replaceAll("\\s+", " ");

        return result.trim();
    }
}
