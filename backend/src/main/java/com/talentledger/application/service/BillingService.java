package com.talentledger.application.service;

import com.talentledger.application.port.inbound.BillingUseCase;
import com.talentledger.application.port.outbound.BillingPort;
import com.talentledger.domain.shared.Result;
import com.talentledger.domain.user.User;
import com.talentledger.domain.user.UserPlan;
import com.talentledger.domain.user.UserQuotaRepository;
import com.talentledger.domain.user.UserRepository;
import com.talentledger.infrastructure.persistence.entity.BillingCycle;
import com.talentledger.infrastructure.persistence.entity.SubscriptionEntity;
import com.talentledger.infrastructure.persistence.entity.SubscriptionEventEntity;
import com.talentledger.infrastructure.persistence.entity.SubscriptionEventType;
import com.talentledger.infrastructure.persistence.entity.SubscriptionStatus;
import com.talentledger.infrastructure.persistence.repository.JpaSubscriptionEventRepository;
import com.talentledger.infrastructure.persistence.repository.JpaSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService implements BillingUseCase {

    private final BillingPort billingPort;
    private final UserRepository userRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final JpaSubscriptionRepository subscriptionRepository;
    private final JpaSubscriptionEventRepository subscriptionEventRepository;

    @Value("${talentledger.billing.stripe.price-pro-monthly:}")
    private String priceProMonthly;
    @Value("${talentledger.billing.stripe.price-pro-yearly:}")
    private String priceProYearly;
    @Value("${talentledger.billing.stripe.price-team-monthly:}")
    private String priceTeamMonthly;
    @Value("${talentledger.billing.stripe.price-team-yearly:}")
    private String priceTeamYearly;

    @Value("${talentledger.billing.success-url}")
    private String successUrl;
    @Value("${talentledger.billing.cancel-url}")
    private String cancelUrl;
    @Value("${talentledger.billing.portal-return-url}")
    private String portalReturnUrl;

    @Override
    public Result<String, String> startCheckout(UUID userId, String plan, String billingCycle) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return Result.failure("User not found");

        UserPlan targetPlan;
        try {
            targetPlan = UserPlan.valueOf(plan.toUpperCase());
        } catch (Exception e) {
            return Result.failure("Unknown plan: " + plan);
        }
        if (targetPlan != UserPlan.PRO && targetPlan != UserPlan.TEAM) {
            return Result.failure("Only PRO or TEAM plans can be purchased via checkout");
        }

        String priceId = resolvePriceId(targetPlan, billingCycle);
        if (priceId == null || priceId.isBlank()) {
            return Result.failure("Billing is not configured for this plan yet");
        }

        String existingCustomerId = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().findFirst().map(SubscriptionEntity::getProviderCustomerId).orElse(null);

        String customerId = billingPort.getOrCreateCustomer(existingCustomerId, user.getEmail(), userId.toString());
        if (customerId == null) {
            return Result.failure("Could not start checkout — billing provider unavailable");
        }

        String normalizedCycle = "YEARLY".equalsIgnoreCase(billingCycle) ? "YEARLY" : "MONTHLY";
        var session = billingPort.createCheckoutSession(
                customerId, priceId, successUrl, cancelUrl,
                Map.of("user_id", userId.toString(), "plan", targetPlan.name(), "billing_cycle", normalizedCycle));

        if (session == null || session.url() == null) {
            return Result.failure("Could not start checkout — billing provider unavailable");
        }
        return Result.success(session.url());
    }

    @Override
    public Result<String, String> startPortalSession(UUID userId) {
        String customerId = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().findFirst().map(SubscriptionEntity::getProviderCustomerId).orElse(null);
        if (customerId == null) {
            return Result.failure("No billing account found for this user yet");
        }
        String url = billingPort.createPortalSession(customerId, portalReturnUrl);
        if (url == null) return Result.failure("Could not open billing portal — provider unavailable");
        return Result.success(url);
    }

    @Override
    public Result<Void, String> cancelSubscription(UUID userId) {
        var sub = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .stream().findFirst().orElse(null);
        if (sub == null) return Result.failure("No active subscription to cancel");
        billingPort.cancelSubscription(sub.getProviderSubscriptionId());
        return Result.success(null);
    }

    @Override
    @Transactional
    public boolean handleWebhook(String payload, String signatureHeader) {
        BillingPort.WebhookEvent event = billingPort.verifyAndParseWebhook(payload, signatureHeader);
        if (event == null) return false;

        if (subscriptionEventRepository.existsByProviderEventId(event.id())) {
            log.info("[BILLING] Duplicate webhook delivery for event {} — skipping", event.id());
            return true;
        }

        try {
            switch (event.type()) {
                case "checkout.session.completed" -> onCheckoutCompleted(event);
                case "customer.subscription.updated" -> onSubscriptionUpdated(event);
                case "customer.subscription.deleted" -> onSubscriptionDeleted(event);
                case "invoice.payment_failed" -> onPaymentFailed(event);
                default -> log.info("[BILLING] Ignoring unhandled Stripe event type {}", event.type());
            }
        } catch (Exception e) {
            // Never let a malformed/unexpected payload shape crash the webhook
            // handler — Stripe will retry a 5xx forever, and we've already
            // verified the signature so this is a real Stripe event we simply
            // failed to process. Log it loudly for manual reconciliation.
            log.error("[BILLING] Failed to process webhook event {} ({}): {}", event.id(), event.type(), e.getMessage(), e);
        }
        return true;
    }

    // ── Webhook handlers ────────────────────────────────────

    private void onCheckoutCompleted(BillingPort.WebhookEvent event) {
        Map<String, Object> data = event.dataObject();
        String customerId = str(data.get("customer"));
        String subscriptionId = str(data.get("subscription"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.getOrDefault("metadata", Map.of());
        String userIdStr = str(metadata.get("user_id"));
        String planStr = str(metadata.get("plan"));
        String cycleStr = str(metadata.get("billing_cycle"));
        if (userIdStr == null || planStr == null) {
            log.error("[BILLING] checkout.session.completed missing user_id/plan metadata, event {}", event.id());
            return;
        }

        UUID userId = UUID.fromString(userIdStr);
        UserPlan newPlan = UserPlan.valueOf(planStr);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.error("[BILLING] checkout.session.completed for unknown user {}", userId);
            return;
        }

        UserPlan oldPlan = user.getPlan();

        SubscriptionEntity sub = SubscriptionEntity.builder()
                .userId(userId)
                .plan(newPlan)
                .status(SubscriptionStatus.ACTIVE)
                .billingCycle("YEARLY".equals(cycleStr) ? BillingCycle.YEARLY : BillingCycle.MONTHLY)
                .startedAt(Instant.now())
                .paymentProvider("STRIPE")
                .providerSubscriptionId(subscriptionId)
                .providerCustomerId(customerId)
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        SubscriptionEntity saved = subscriptionRepository.save(sub);

        user.changePlan(newPlan);
        userRepository.save(user);

        userQuotaRepository.findByUserId(userId).ifPresent(q -> userQuotaRepository.save(q.changePlan(newPlan)));

        logEvent(saved, oldPlan, newPlan,
                oldPlan == UserPlan.FREE ? SubscriptionEventType.CREATED : SubscriptionEventType.UPGRADED, event.id());
    }

    private void onSubscriptionUpdated(BillingPort.WebhookEvent event) {
        Map<String, Object> data = event.dataObject();
        String subscriptionId = str(data.get("id"));
        String status = str(data.get("status"));

        SubscriptionEntity sub = subscriptionRepository.findByProviderSubscriptionId(subscriptionId).orElse(null);
        if (sub == null) {
            log.warn("[BILLING] customer.subscription.updated for unknown subscription {}", subscriptionId);
            return;
        }

        SubscriptionStatus newStatus = mapStripeStatus(status);
        sub.setStatus(newStatus);
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);

        logEvent(sub, sub.getPlan(), sub.getPlan(), SubscriptionEventType.RENEWED, event.id());
    }

    private void onSubscriptionDeleted(BillingPort.WebhookEvent event) {
        Map<String, Object> data = event.dataObject();
        String subscriptionId = str(data.get("id"));

        SubscriptionEntity sub = subscriptionRepository.findByProviderSubscriptionId(subscriptionId).orElse(null);
        if (sub == null) {
            log.warn("[BILLING] customer.subscription.deleted for unknown subscription {}", subscriptionId);
            return;
        }

        UserPlan oldPlan = sub.getPlan();
        sub.setStatus(SubscriptionStatus.CANCELLED);
        sub.setCancelledAt(Instant.now());
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);

        User user = userRepository.findById(sub.getUserId()).orElse(null);
        if (user != null) {
            user.changePlan(UserPlan.FREE);
            userRepository.save(user);
            userQuotaRepository.findByUserId(sub.getUserId())
                    .ifPresent(q -> userQuotaRepository.save(q.changePlan(UserPlan.FREE)));
        }

        logEvent(sub, oldPlan, UserPlan.FREE, SubscriptionEventType.CANCELLED, event.id());
    }

    private void onPaymentFailed(BillingPort.WebhookEvent event) {
        Map<String, Object> data = event.dataObject();
        String customerId = str(data.get("customer"));
        if (customerId == null) return;

        SubscriptionEntity sub = subscriptionRepository
                .findFirstByProviderCustomerIdOrderByCreatedAtDesc(customerId).orElse(null);
        if (sub == null) return;

        sub.setStatus(SubscriptionStatus.PAST_DUE);
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);

        logEvent(sub, sub.getPlan(), sub.getPlan(), SubscriptionEventType.PAYMENT_FAILED, event.id());
    }

    // ── Helpers ─────────────────────────────────────────────

    private void logEvent(SubscriptionEntity sub, UserPlan from, UserPlan to, SubscriptionEventType type, String providerEventId) {
        subscriptionEventRepository.save(SubscriptionEventEntity.builder()
                .subscriptionId(sub.getId())
                .userId(sub.getUserId())
                .eventType(type)
                .fromPlan(from)
                .toPlan(to)
                .providerEventId(providerEventId)
                .createdAt(Instant.now())
                .build());
    }

    private SubscriptionStatus mapStripeStatus(String stripeStatus) {
        if (stripeStatus == null) return SubscriptionStatus.ACTIVE;
        return switch (stripeStatus) {
            case "active", "trialing" -> SubscriptionStatus.ACTIVE;
            case "past_due", "unpaid" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELLED;
            default -> SubscriptionStatus.ACTIVE;
        };
    }

    private String resolvePriceId(UserPlan plan, String billingCycle) {
        boolean yearly = "YEARLY".equalsIgnoreCase(billingCycle);
        if (plan == UserPlan.PRO) return yearly ? priceProYearly : priceProMonthly;
        if (plan == UserPlan.TEAM) return yearly ? priceTeamYearly : priceTeamMonthly;
        return null;
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
