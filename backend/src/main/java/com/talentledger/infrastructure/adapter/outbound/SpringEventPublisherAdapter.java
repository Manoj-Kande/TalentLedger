package com.talentledger.infrastructure.adapter.outbound;

import com.talentledger.application.port.outbound.EventPublisherPort;
import com.talentledger.domain.shared.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring ApplicationEvent publisher adapter.
 * Bridges domain events to Spring's event bus.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringEventPublisherAdapter implements EventPublisherPort {

    private final ApplicationEventPublisher publisher;

    @Override
    public void publish(DomainEvent event) {
        log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
        publisher.publishEvent(event);
    }

    @Override
    public void publish(Object event) {
        log.debug("Publishing event: {}", event.getClass().getSimpleName());
        publisher.publishEvent(event);
    }
}
