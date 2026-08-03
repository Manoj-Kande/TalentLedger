package com.talentledger.application.port.outbound;

import java.util.Map;

/**
 * Outbound port — payment/subscription provider (provider-agnostic per the
 * architecture doc's "swap providers behind an interface" principle).
 * Implemented by {@code StripeBillingAdapter}.
 */
public interface BillingPort {

    /** Create (or reuse) a customer record with the provider for this user. */
    String getOrCreateCustomer(String existingCustomerId, String userEmail, String userId);

    /** Create a hosted checkout session for a subscription purchase. Returns the checkout URL. */
    CheckoutSession createCheckoutSession(String customerId, String priceId, String successUrl,
                                           String cancelUrl, Map<String, String> metadata);

    /** Create a hosted billing-portal session (manage payment method, cancel, invoices). Returns the portal URL. */
    String createPortalSession(String customerId, String returnUrl);

    /** Cancel a subscription (at period end) with the provider. */
    void cancelSubscription(String providerSubscriptionId);

    /**
     * Verify a webhook payload's signature and return the raw parsed event, or
     * null if the signature doesn't verify (caller must reject with 400 in that case).
     */
    WebhookEvent verifyAndParseWebhook(String payload, String signatureHeader);

    record CheckoutSession(String url, String sessionId) {}

    record WebhookEvent(String id, String type, Map<String, Object> dataObject) {}
}
