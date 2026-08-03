package com.talentledger.infrastructure.adapter.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentledger.application.port.outbound.AiClientPort;
import com.talentledger.domain.contact.Contact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real AI enrichment/cold-email/hiring-signal adapter, backed by the
 * Anthropic Messages API (https://api.anthropic.com/v1/messages).
 *
 * <p>Implemented against the JDK's {@link HttpClient} rather than an SDK
 * dependency, for the same reason as {@link ResendEmailAdapter} — this
 * environment can't pull/verify a new Maven dependency, and the Messages
 * API is a single JSON POST.
 *
 * <p>Only registered when {@code talentledger.ai.provider=ANTHROPIC} (see
 * application.yml / ANTHROPIC_API_KEY env var). Falls back to
 * {@link StubAiClientAdapter} otherwise so nothing breaks for anyone who
 * hasn't configured a key — AI enrichment is a Pro/Team feature gated on
 * quota anyway (ai_credits_limit = 0 on Free), so a missing key degrades to
 * the existing placeholder behavior rather than failing requests.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "talentledger.ai", name = "provider", havingValue = "ANTHROPIC")
public class AnthropicAiClientAdapter implements AiClientPort {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${talentledger.ai.anthropic.api-key:}")
    private String apiKey;

    @Value("${talentledger.ai.anthropic.model:claude-sonnet-4-6}")
    private String model;

    @Override
    public AiEnrichmentResult enrichContact(Contact contact, String enrichmentType) {
        String prompt = """
                You are enriching a CRM contact record. Given this contact's known fields, \
                write a concise (2-4 sentence) %s about them for a recruiter/salesperson to skim \
                before reaching out. Only state things reasonably inferable from the fields given \
                (title, seniority, company/domain) — do not invent specifics you weren't given.

                Name: %s
                Title: %s
                Seniority: %s
                Company domain: %s
                Location: %s
                """.formatted(
                enrichmentType != null ? enrichmentType : "summary",
                nullToDash(contact.getName()),
                nullToDash(contact.getTitle()),
                contact.getSeniorityLevel() != null ? contact.getSeniorityLevel().toString() : "-",
                nullToDash(contact.getDomain()),
                nullToDash(contact.getLocation())
        );

        CallResult result = callClaude(prompt, 300);
        if (result == null) {
            return fallback(contact, enrichmentType);
        }
        return new AiEnrichmentResult(result.text(), enrichmentType, model,
                result.inputTokens(), result.outputTokens(), 0.8);
    }

    @Override
    public String generateColdEmail(Contact contact, String tone) {
        String prompt = """
                Write a short (under 120 words), %s cold outreach email to this contact. \
                No subject line, just the body. Do not use generic filler like "I hope this finds you well". \
                Sign off as "[Your name]".

                Name: %s
                Title: %s
                Company domain: %s
                """.formatted(
                tone != null ? tone : "professional",
                nullToDash(contact.getName()),
                nullToDash(contact.getTitle()),
                nullToDash(contact.getDomain())
        );

        CallResult result = callClaude(prompt, 300);
        return result != null ? result.text()
                : "Dear " + contact.getName() + ",\n\n[AI enrichment unavailable]\n\nBest regards";
    }

    @Override
    public String analyzeHiringSignal(Contact contact) {
        String prompt = """
                Based only on the fields below, output a compact JSON object (no prose, no markdown fences) \
                with keys "signal" (one of: "likely_hiring", "unclear", "unlikely_hiring") and "reasoning" \
                (one short sentence). Do not fabricate information not implied by the fields.

                Title: %s
                Seniority: %s
                Company domain: %s
                """.formatted(nullToDash(contact.getTitle()), 
                contact.getSeniorityLevel() != null ? contact.getSeniorityLevel().toString() : "-",
                nullToDash(contact.getDomain()));

        CallResult result = callClaude(prompt, 150);
        return result != null ? result.text() : "{\"signal\": \"unclear\", \"reasoning\": \"AI enrichment unavailable\"}";
    }

    // ── Internal ─────────────────────────────────────────────

    private AiEnrichmentResult fallback(Contact contact, String enrichmentType) {
        return new AiEnrichmentResult(
                "AI enrichment unavailable for " + contact.getName(), enrichmentType, model, 0, 0, 0.0);
    }

    private String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    private record CallResult(String text, int inputTokens, int outputTokens) {}

    private CallResult callClaude(String prompt, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[AI] ANTHROPIC provider selected but no API key configured");
            return null;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("[AI] Anthropic API returned {}: {}", response.statusCode(), response.body());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            StringBuilder text = new StringBuilder();
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            int inputTokens = root.path("usage").path("input_tokens").asInt(0);
            int outputTokens = root.path("usage").path("output_tokens").asInt(0);

            return new CallResult(text.toString().trim(), inputTokens, outputTokens);
        } catch (Exception e) {
            log.error("[AI] Anthropic call failed: {}", e.getMessage(), e);
            return null;
        }
    }
}
