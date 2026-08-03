package com.talentledger.shared.exception;

public class RateLimitException extends DomainException {
    public RateLimitException(String message) {
        super("RATE_LIMITED", message, 429);
    }
}
