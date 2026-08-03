package com.talentledger.infrastructure.adapter.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentledger.application.port.outbound.BillingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Real Stripe billing adapter using Stripe's plain REST API
 * (https://api.stripe.com/v1/...) via the JDK's {@link HttpClient}, rather
 * than the stripe-java SDK — this environment can't pull/verify a new Maven
 * dependency, and Stripe's REST surface (form-encoded POSTs, JSON responses)
 * doesn't require one.
 *
 * <p>Only actually functions once {@code STRIPE_SECRET_KEY} is configured;
 * with no key set, methods log and no-op / return null rather than throwing,
 * so the app still boots and every non-billing feature keeps working.
 */
@Slf4j
@Component
public class StripeBillingAdapter implements BillingPort {

    private static final String API_BASE = "https://api.stripe.com/v1";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${talentledger.billing.stripe.secret-key:}")
    private String secretKey;

    @Value("${talentledger.billing.stripe.webhook-secret:}")
    private String webhookSecret;

    private boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    @Override
    public String getOrCreateCustomer(String existingCustomerId, String userEmail, String userId) {
        if (existingCustomerId != null && !existingCustomerId.isBlank()) {
            return existingCustomerId;
        }
        if (!isConfigured()) {
            log.warn("[BILLING] Stripe not configured — cannot create customer for {}", userEmail);
            return null;
        }

        Map<String, String> form = new HashMap<>();
        form.put("email", userEmail);
        form.put("metadata[user_id]", userId);

        JsonNode result = post("/customers", form);
        return result != null ? result.path("id").asText(null) : null;
    }

    @Override
    public CheckoutSession createCheckoutSession(String customerId, String priceId, String successUrl,
                                                  String cancelUrl, Map<String, String> metadata) {
        if (!isConfigured()) {
            log.warn("[BILLING] Stripe not configured — cannot create checkout session");
            return null;
        }

        Map<String, String> form = new HashMap<>();
        form.put("mode", "subscription");
        form.put("customer", customerId);
        form.put("line_items[0][price]", priceId);
        form.put("line_items[0][quantity]", "1");
        form.put("success_url", successUrl);
        form.put("cancel_url", cancelUrl);
        form.put("allow_promotion_codes", "true");
        if (metadata != null) {
            metadata.forEach((k, v) -> form.put("metadata[" + k + "]", v));
            // Also stamp metadata onto the subscription itself, not just the
            // checkout session, so subscription.updated webhooks (which don't
            // carry the checkout session's metadata) can still resolve the user.
            metadata.forEach((k, v) -> form.put("subscription_data[metadata][" + k + "]", v));
        }

        JsonNode result = post("/checkout/sessions", form);
        if (result == null) return null;
        return new CheckoutSession(result.path("url").asText(null), result.path("id").asText(null));
    }

    @Override
    public String createPortalSession(String customerId, String returnUrl) {
        if (!isConfigured()) {
            log.warn("[BILLING] Stripe not configured — cannot create portal session");
            return null;
        }

        Map<String, String> form = new HashMap<>();
        form.put("customer", customerId);
        form.put("return_url", returnUrl);

        JsonNode result = post("/billing_portal/sessions", form);
        return result != null ? result.path("url").asText(null) : null;
    }

    @Override
    public void cancelSubscription(String providerSubscriptionId) {
        if (!isConfigured() || providerSubscriptionId == null) {
            log.warn("[BILLING] Stripe not configured or no subscription id — cannot cancel");
            return;
        }
        Map<String, String> form = new HashMap<>();
        form.put("cancel_at_period_end", "true");
        post("/subscriptions/" + providerSubscriptionId, form);
    }

    @Override
    public WebhookEvent verifyAndParseWebhook(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("[BILLING] No Stripe webhook secret configured — refusing to trust unverified webhook");
            return null;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("[BILLING] Webhook received with no Stripe-Signature header");
            return null;
        }

        // Header format: "t=1614556800,v1=5257a869e7...,v1=..."
        String timestamp = null;
        java.util.List<String> v1Signatures = new java.util.ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            if ("t".equals(kv[0])) timestamp = kv[1];
            else if ("v1".equals(kv[0])) v1Signatures.add(kv[1]);
        }
        if (timestamp == null || v1Signatures.isEmpty()) {
            log.warn("[BILLING] Malformed Stripe-Signature header");
            return null;
        }

        String signedPayload = timestamp + "." + payload;
        String expected = hmacSha256Hex(webhookSecret, signedPayload);
        boolean matches = v1Signatures.stream().anyMatch(sig -> constantTimeEquals(sig, expected));
        if (!matches) {
            log.warn("[BILLING] Stripe webhook signature verification failed");
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String id = root.path("id").asText(null);
            String type = root.path("type").asText(null);
            JsonNode dataObjectNode = root.path("data").path("object");
            Map<String, Object> dataObject = objectMapper.convertValue(dataObjectNode, Map.class);
            return new WebhookEvent(id, type, dataObject);
        } catch (Exception e) {
            log.error("[BILLING] Failed to parse verified webhook payload: {}", e.getMessage(), e);
            return null;
        }
    }

    // ── Internal ─────────────────────────────────────────────

    private JsonNode post(String path, Map<String, String> form) {
        try {
            StringJoiner body = new StringJoiner("&");
            for (Map.Entry<String, String> entry : form.entrySet()) {
                if (entry.getValue() == null) continue;
                body.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + path))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readTree(response.body());
            }
            log.error("[BILLING] Stripe API {} returned {}: {}", path, response.statusCode(), response.body());
            return null;
        } catch (Exception e) {
            log.error("[BILLING] Stripe API call to {} failed: {}", path, e.getMessage(), e);
            return null;
        }
    }

    private String hmacSha256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
