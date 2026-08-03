package com.talentledger.infrastructure.web.controller;

import com.talentledger.application.port.inbound.BillingUseCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingUseCase billingUseCase;

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> checkout(
            @RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = billingUseCase.startCheckout(userId, body.get("plan"), body.get("billingCycle"));
        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", Map.of("checkoutUrl", result.getValue())))
                : ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
    }

    @PostMapping("/portal")
    public ResponseEntity<Map<String, Object>> portal(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = billingUseCase.startPortalSession(userId);
        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", Map.of("portalUrl", result.getValue())))
                : ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
    }

    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancel(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = billingUseCase.cancelSubscription(userId);
        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true))
                : ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
    }

    /**
     * Stripe webhook receiver. Public path (see SessionAuthFilter.PUBLIC_PATHS /
     * SecurityConfig) — authenticity is established via Stripe-Signature
     * verification inside BillingService, not session auth. Must read the raw
     * body (not a parsed DTO) since signature verification is over exact bytes.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            HttpServletRequest request,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) throws IOException {
        String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean accepted = billingUseCase.handleWebhook(payload, signature);
        return accepted ? ResponseEntity.ok("ok") : ResponseEntity.badRequest().body("invalid signature");
    }

    private UUID getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new com.talentledger.shared.exception.UnauthorizedException(
                    "No authenticated user on request (SessionAuthFilter should have rejected this earlier)");
        }
        return userId instanceof UUID ? (UUID) userId : UUID.fromString(userId.toString());
    }
}
