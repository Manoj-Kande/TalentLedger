package com.talentledger.shared.exception;

/**
 * Thrown when a requested resource is not found. Maps to 404.
 * Uses same error message for "user not found" and "wrong password"
 * to prevent enumeration (ADR security rule).
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String errorCode, String message) {
        super(errorCode, message, 404);
    }

    public NotFoundException(String message) {
        super("NOT_FOUND", message, 404);
    }
}
