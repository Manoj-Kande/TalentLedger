package com.talentledger.infrastructure.persistence.entity;

/**
 * Subscription statuses stored in the {@code subscriptions.status} column.
 */
public enum SubscriptionStatus {
    ACTIVE,
    CANCELLED,
    EXPIRED,
    PAST_DUE,
    TRIAL
}
