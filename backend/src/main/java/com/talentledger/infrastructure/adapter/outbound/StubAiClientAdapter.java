package com.talentledger.infrastructure.adapter.outbound;

import com.talentledger.application.port.outbound.AiClientPort;
import com.talentledger.application.port.outbound.AiClientPort.AiEnrichmentResult;
import com.talentledger.domain.contact.Contact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub AI client adapter for development.
 * Returns placeholder responses.
 *
 * <p>Active whenever {@code talentledger.ai.provider} is unset or not
 * {@code ANTHROPIC} — i.e. the default. Set {@code talentledger.ai.provider=ANTHROPIC}
 * (and {@code ANTHROPIC_API_KEY}) to switch to {@link AnthropicAiClientAdapter} for
 * real enrichment/cold-email/hiring-signal generation.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "talentledger.ai", name = "provider", havingValue = "STUB", matchIfMissing = true)
public class StubAiClientAdapter implements AiClientPort {

    @Override
    public AiEnrichmentResult enrichContact(Contact contact, String enrichmentType) {
        log.info("[AI-STUB] Enrichment request for contact {} - type: {}", contact.getId(), enrichmentType);
        return new AiEnrichmentResult(
                "AI-generated enrichment placeholder for " + contact.getName(),
                enrichmentType,
                "stub-model-v1",
                50,
                100,
                0.5
        );
    }

    @Override
    public String generateColdEmail(Contact contact, String tone) {
        log.info("[AI-STUB] Cold email generation for contact {} - tone: {}", contact.getId(), tone);
        return "Dear " + contact.getName() + ",\n\n[AI-generated cold email placeholder]\n\nBest regards";
    }

    @Override
    public String analyzeHiringSignal(Contact contact) {
        log.info("[AI-STUB] Hiring signal analysis for contact {}", contact.getId());
        return "{\"signal\": \"placeholder\", \"confidence\": 0.0}";
    }
}
