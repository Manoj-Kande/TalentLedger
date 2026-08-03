package com.talentledger.domain.contact;

/**
 * Lifecycle status of a Contact aggregate.
 *
 * <p>Pure enum — zero framework dependency.
 */
public enum ContactStatus {

    /** Contact is actively managed and visible in the UI. */
    ACTIVE,

    /** Contact has been archived by the user; hidden from default views. */
    ARCHIVED,

    /** Contact has been soft-deleted and is pending permanent removal. */
    DELETED
}
