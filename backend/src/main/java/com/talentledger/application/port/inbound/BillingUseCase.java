package com.talentledger.application.port.inbound;

import com.talentledger.domain.shared.Result;

import java.util.UUID;

public interface BillingUseCase {

    Result<String, String> startCheckout(UUID userId, String plan, String billingCycle);

    Result<String, String> startPortalSession(UUID userId);

    Result<Void, String> cancelSubscription(UUID userId);

    /** Returns true if the webhook was accepted (valid signature). Business errors inside are logged, not thrown. */
    boolean handleWebhook(String payload, String signatureHeader);
}
