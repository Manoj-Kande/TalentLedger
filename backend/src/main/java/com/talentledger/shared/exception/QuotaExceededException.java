package com.talentledger.shared.exception;

public class QuotaExceededException extends DomainException {
    public QuotaExceededException(String quotaType, String limit) {
        super("QUOTA_EXCEEDED", quotaType + " limit reached (" + limit + "). Upgrade to continue.", 429);
    }

    public QuotaExceededException(String message) {
        super("QUOTA_EXCEEDED", message, 429);
    }
}
