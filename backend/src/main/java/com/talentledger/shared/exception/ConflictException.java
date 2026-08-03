package com.talentledger.shared.exception;

public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super("CONFLICT", message, 409);
    }

    public ConflictException(String code, String message) {
        super(code, message, 409);
    }
}
