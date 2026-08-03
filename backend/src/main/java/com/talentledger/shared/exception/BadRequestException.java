package com.talentledger.shared.exception;

public class BadRequestException extends DomainException {
    public BadRequestException(String message) {
        super("BAD_REQUEST", message, 400);
    }
}
