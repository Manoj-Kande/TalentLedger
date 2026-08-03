package com.talentledger.application.port.outbound;

import com.talentledger.domain.contact.Contact;

/**
 * Outbound port — AI enrichment client.
 * Implemented by external AI service adapters (future: OpenAI, Claude).
 * Currently a placeholder for Phase 3+.
 */
public interface AiClientPort {

    /** Enrich a contact with AI-generated content. */
    AiEnrichmentResult enrichContact(Contact contact, String enrichmentType);

    /** Generate a cold email draft. */
    String generateColdEmail(Contact contact, String tone);

    /** Get a hiring signal analysis. */
    String analyzeHiringSignal(Contact contact);

    // ── Inner types ──────────────────────────────────────

    record AiEnrichmentResult(
        String generatedContent,
        String enrichmentType,
        String modelUsed,
        int promptTokens,
        int completionTokens,
        double confidenceScore
    ) {}
}
