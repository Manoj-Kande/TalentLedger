package com.talentledger.shared.exception;

public class ValidationException extends DomainException {

    private final java.util.Map<String, String> fieldErrors;

    public ValidationException(String field, String message) {
        super("VALIDATION_FAILED", field + ": " + message, 422);
        this.fieldErrors = java.util.Map.of(field, message);
    }

    public ValidationException(java.util.Map<String, String> fieldErrors) {
        super("VALIDATION_FAILED", "Validation failed: " + fieldErrors.size() + " field(s)", 422);
        this.fieldErrors = fieldErrors;
    }

    public java.util.Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
