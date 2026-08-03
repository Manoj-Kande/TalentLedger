package com.talentledger.infrastructure.persistence.entity;

/**
 * Who initiated the email change, stored in the {@code email_change_history.changed_by} column.
 */
public enum EmailChangeReason {
    USER,
    CLERK_WEBHOOK,
    ADMIN
}
