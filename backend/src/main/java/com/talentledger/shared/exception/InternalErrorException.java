package com.talentledger.shared.exception;

/**
 * Internal error — should never reach the user with details.
 * Logged at ERROR level. Generic message returned to client.
 */
public class InternalErrorException extends DomainException {
    public InternalErrorException(String message) {
        super("INTERNAL_ERROR", message, 500);
    }

    public InternalErrorException(String message, Throwable cause) {
        super("INTERNAL_ERROR", message, 500);
        initCause(cause);
    }
}
