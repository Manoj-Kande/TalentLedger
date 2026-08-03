package com.talentledger.shared.exception;

/**
 * Base exception for all domain/business violations.
 * Carries an error code from our 40+ code catalog.
 */
public class DomainException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public DomainException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public DomainException(String errorCode, String message) {
        this(errorCode, message, 400);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
