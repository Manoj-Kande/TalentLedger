package com.talentledger.domain.dump;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable value object summarising the outcome of a dump parse operation.
 *
 * <p>Captures row counts, duplicate counts, error details, and timing.
 * Use the static factory {@link #of} for construction.
 */
public final class ParseResult {

    private static final int MAX_ERRORS = 100;

    private final int totalRows;
    private final int parsedContactsCount;
    private final int duplicateWithinDumpCount;
    private final int crossDumpDuplicateCount;
    private final int errorCount;
    private final long parseDurationMs;
    private final List<String> parseErrors;

    private ParseResult(int totalRows,
                        int parsedContactsCount,
                        int duplicateWithinDumpCount,
                        int crossDumpDuplicateCount,
                        int errorCount,
                        long parseDurationMs,
                        List<String> parseErrors) {
        this.totalRows = totalRows;
        this.parsedContactsCount = parsedContactsCount;
        this.duplicateWithinDumpCount = duplicateWithinDumpCount;
        this.crossDumpDuplicateCount = crossDumpDuplicateCount;
        this.errorCount = errorCount;
        this.parseDurationMs = parseDurationMs;
        this.parseErrors = Collections.unmodifiableList(new ArrayList<>(parseErrors));
    }

    /**
     * Factory method for constructing a ParseResult.
     *
     * <p>Parse errors are capped at {@value #MAX_ERRORS} entries.
     *
     * @param totalRows              total rows found in the file
     * @param parsedContactsCount    number of successfully parsed contacts
     * @param duplicateWithinDumpCount duplicates found within this dump
     * @param crossDumpDuplicateCount duplicates found across dumps for the same user
     * @param parseErrors             list of error messages (may be null/empty)
     * @param parseDurationMs         duration of the parse in milliseconds
     * @return a new ParseResult
     */
    public static ParseResult of(int totalRows,
                                 int parsedContactsCount,
                                 int duplicateWithinDumpCount,
                                 int crossDumpDuplicateCount,
                                 List<String> parseErrors,
                                 long parseDurationMs) {
        List<String> cappedErrors = new ArrayList<>();
        if (parseErrors != null) {
            int limit = Math.min(parseErrors.size(), MAX_ERRORS);
            for (int i = 0; i < limit; i++) {
                cappedErrors.add(parseErrors.get(i));
            }
        }

        return new ParseResult(
                totalRows,
                parsedContactsCount,
                duplicateWithinDumpCount,
                crossDumpDuplicateCount,
                cappedErrors.size(),
                parseDurationMs,
                cappedErrors
        );
    }

    /**
     * Returns the success rate as a value between 0.0 and 1.0.
     *
     * <p>Calculated as {@code parsedContactsCount / totalRows}.
     * Returns {@code 1.0} when there are zero total rows (avoids division by zero).
     */
    public double successRate() {
        if (totalRows <= 0) {
            return 1.0;
        }
        return (double) parsedContactsCount / totalRows;
    }

    /**
     * Returns {@code true} if any parse errors were recorded.
     */
    public boolean hasErrors() {
        return errorCount > 0;
    }

    // -- Getters --

    public int getTotalRows() {
        return totalRows;
    }

    public int getParsedContactsCount() {
        return parsedContactsCount;
    }

    public int getDuplicateWithinDumpCount() {
        return duplicateWithinDumpCount;
    }

    public int getCrossDumpDuplicateCount() {
        return crossDumpDuplicateCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public long getParseDurationMs() {
        return parseDurationMs;
    }

    public List<String> getParseErrors() {
        return parseErrors;
    }
}
