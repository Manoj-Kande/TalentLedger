package com.talentledger.application.port.outbound;

import com.talentledger.domain.shared.DomainEvent;

/**
 * Outbound port — Domain event publisher.
 * Wraps Spring ApplicationEventPublisher behind a domain-friendly interface.
 * ADR-013: Outbox pattern — events go to outbox_events table first,
 * then this port is called by the OutboxPoller.
 */
public interface EventPublisherPort {

    /** Publish a domain event to Spring's event bus. */
    void publish(DomainEvent event);

    /** Publish a raw event (used by OutboxPoller). */
    void publish(Object event);
}
