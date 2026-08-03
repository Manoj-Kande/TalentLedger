package com.talentledger.domain.shared;

/**
 * Thrown when a declared business rule is violated.
 *
 * <p>This is for expected domain-level violations (e.g. "email already exists",
 * "quota exceeded"). It should NOT be used for unexpected system errors.
 */
public class BusinessRuleViolationException extends RuntimeException {

    private final String ruleCode;

    public BusinessRuleViolationException(String message) {
        super(message);
        this.ruleCode = "BUSINESS_RULE_VIOLATED";
    }

    public BusinessRuleViolationException(String ruleCode, String message) {
        super(message);
        this.ruleCode = ruleCode;
    }

    public String getRuleCode() {
        return ruleCode;
    }
}
