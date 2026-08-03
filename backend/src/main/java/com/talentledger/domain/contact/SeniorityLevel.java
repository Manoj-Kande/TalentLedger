package com.talentledger.domain.contact;

/**
 * Professional seniority band for a contact.
 *
 * <p>Ordered from individual contributor up to founder-level.
 * Pure enum — zero framework dependency.
 */
public enum SeniorityLevel {

    /** Individual contributor — no direct reports. */
    IC,

    /** First-line people manager. */
    MANAGER,

    /** Senior manager / department director. */
    DIRECTOR,

    /** Vice president level. */
    VP,

    /** C-suite executive (CEO, CTO, CFO, etc.). */
    CXO,

    /** Company founder or co-founder. */
    FOUNDER
}
