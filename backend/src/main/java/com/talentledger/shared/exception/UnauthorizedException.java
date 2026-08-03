package com.talentledger.shared.exception;

public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, 401);
    }

    public UnauthorizedException(String code, String message) {
        super(code, message, 401);
    }
}
