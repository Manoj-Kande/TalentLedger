package com.talentledger.domain.shared;

import java.time.Instant;
import java.util.UUID;

/**
 * Base domain event. Immutable.
 *
 * <p>Domain events are collected on AggregateRoot and drained by the
 * OutboxPoller into Spring ApplicationEvents for reliable publishing.
 */
public abstract class DomainEvent {

    private final UUID eventId;
    private final Instant occurredAt;

    protected DomainEvent() {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
