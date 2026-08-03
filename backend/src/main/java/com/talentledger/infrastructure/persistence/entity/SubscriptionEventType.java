package com.talentledger.infrastructure.persistence.entity;

/**
 * Subscription event types stored in the {@code subscription_events.event_type} column.
 */
public enum SubscriptionEventType {
    CREATED,
    RENEWED,
    UPGRADED,
    DOWNGRADED,
    CANCELLED,
    PAYMENT_FAILED,
    PAYMENT_SUCCEEDED
}
