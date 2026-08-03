package com.talentledger.domain.dump;

/**
 * Lifecycle status of a {@link DataDump}.
 *
 * <p>Tracks the progression from upload through parsing to completion or failure.
 * Pure Java enum — zero framework dependency.
 */
public enum DumpStatus {

    /** Newly uploaded, not yet parsed. */
    PENDING,

    /** Currently being parsed. */
    PARSING,

    /** Parsing finished — all rows were processed (may still contain errors). */
    COMPLETED,

    /** Parsing or processing failed due to an error. */
    FAILED,

    /** User cancelled the import before parsing completed. */
    CANCELLED,

    /** Free-tier dump expired and was auto-cleaned up. */
    EXPIRED
}
