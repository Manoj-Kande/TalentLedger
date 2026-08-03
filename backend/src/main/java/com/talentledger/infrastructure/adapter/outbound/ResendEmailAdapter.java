package com.talentledger.infrastructure.adapter.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentledger.application.port.outbound.EmailSenderPort;
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
import java.util.Map;

/**
 * Real email adapter backed by the Resend HTTP API
 * (https://resend.com/docs/api-reference/emails/send-email).
 *
 * <p>Deliberately implemented with the JDK's built-in {@link HttpClient}
 * rather than a new Resend SDK dependency — this sandbox/build environment
 * cannot reach Maven Central to pull in and verify a new dependency, and
 * Resend's API is a single simple POST, so no SDK is actually needed.
 *
 * <p>Only registered when {@code talentledger.email.provider=RESEND} (see
 * application.yml / RESEND_API_KEY env var). Falls back to
 * {@link ConsoleEmailAdapter} otherwise, so local dev with no key configured
 * keeps working exactly as before — nothing breaks if this adapter is never
 * activated.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "talentledger.email", name = "provider", havingValue = "RESEND")
public class ResendEmailAdapter implements EmailSenderPort {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${talentledger.email.resend.api-key:}")
    private String apiKey;

    @Value("${talentledger.email.from-address:noreply@talentledger.app}")
    private String defaultFrom;

    @Override
    public void send(EmailMessage message) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[EMAIL] RESEND provider selected but no API key configured — dropping email to {}", message.to());
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", message.from() != null ? message.from() : defaultFrom);
        body.put("to", java.util.List.of(message.to()));
        body.put("subject", message.subject());
        if (message.htmlBody() != null) body.put("html", message.htmlBody());
        if (message.textBody() != null) body.put("text", message.textBody());

        sendRaw(body, message.to());
    }

    @Override
    public void sendTemplated(String to, String templateId, Map<String, Object> variables) {
        // Resend doesn't have server-side templates the way SendGrid does —
        // the caller is expected to have already rendered the template into
        // subject/html before reaching this port in a fuller implementation.
        // For now, ship a minimal templated fallback so nothing silently
        // no-ops: render the variables into a simple readable body.
        StringBuilder html = new StringBuilder("<p>Template: ").append(templateId).append("</p><ul>");
        variables.forEach((k, v) -> html.append("<li>").append(k).append(": ").append(v).append("</li>"));
        html.append("</ul>");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", defaultFrom);
        body.put("to", java.util.List.of(to));
        body.put("subject", "TalentLedger: " + templateId);
        body.put("html", html.toString());

        sendRaw(body, to);
    }

    private void sendRaw(Map<String, Object> body, String to) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[EMAIL] Sent via Resend to {} (status {})", to, response.statusCode());
            } else {
                // Never let an email provider outage take down the calling
                // request (registration, password reset, etc.) — log and
                // move on. Callers that truly need delivery confirmation
                // should check outbox/audit logs, not this call's return.
                log.error("[EMAIL] Resend API returned {} for {}: {}", response.statusCode(), to, response.body());
            }
        } catch (Exception e) {
            log.error("[EMAIL] Failed to send via Resend to {}: {}", to, e.getMessage(), e);
        }
    }
}
