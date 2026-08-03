package com.talentledger.infrastructure.web.advice;

import com.talentledger.shared.exception.DomainException;
import com.talentledger.shared.exception.NotFoundException;
import com.talentledger.shared.exception.QuotaExceededException;
import com.talentledger.shared.exception.RateLimitException;
import com.talentledger.shared.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global Exception Handler — maps domain exceptions to standardized error responses.
 *
 * Response envelope:
 * <pre>
 * {
 *   "success": false,
 *   "error": {
 *     "code": "QUOTA_EXCEEDED",
 *     "message": "...",
 *     "details": {},
 *     "fieldErrors": null,
 *     "requestId": "uuid",
 *     "timestamp": "..."
 *   }
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Envelope> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        // Thrown by AuthService.login() on bad credentials, register on duplicate email,
        // and similar "expected" validation failures elsewhere. This is NOT a server error —
        // map to 400 so it doesn't get swallowed by the generic 500 handler, and log it at
        // WARN (visible on console) instead of ERROR with a full stack trace.
        log.warn("Bad request on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());

        ErrorResponse error = new ErrorResponse(
                "BAD_REQUEST", ex.getMessage(), null, null, getRequestId(), Instant.now());
        return ResponseEntity.status(400).body(new Envelope(false, null, error));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Envelope> handleDomainException(DomainException ex, HttpServletRequest request) {
        int status = ex.getHttpStatus();
        ErrorResponse error = new ErrorResponse(
                ex.getErrorCode(),
                ex.getMessage(),
                null,
                null,
                getRequestId(),
                Instant.now()
        );

        if (status >= 500) {
            log.error("Domain exception [{}]: {}", ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            log.warn("Domain exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        }

        return ResponseEntity.status(status).body(new Envelope(false, null, error));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Envelope> handleNotFound(NotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
                ex.getErrorCode(), ex.getMessage(), null, null, getRequestId(), Instant.now());
        return ResponseEntity.status(404).body(new Envelope(false, null, error));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Envelope> handleValidation(ValidationException ex) {
        ErrorResponse error = new ErrorResponse(
                ex.getErrorCode(), "Validation failed", null, ex.getFieldErrors(), getRequestId(), Instant.now());
        return ResponseEntity.status(422).body(new Envelope(false, null, error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Envelope> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value"
                ));

        ErrorResponse error = new ErrorResponse(
                "VALIDATION_FAILED", "Request validation failed", null, fieldErrors, getRequestId(), Instant.now());
        return ResponseEntity.status(400).body(new Envelope(false, null, error));
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<Envelope> handleQuotaExceeded(QuotaExceededException ex) {
        ErrorResponse error = new ErrorResponse(
                ex.getErrorCode(), ex.getMessage(), null, null, getRequestId(), Instant.now());
        return ResponseEntity.status(429).body(new Envelope(false, null, error));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Envelope> handleRateLimit(RateLimitException ex) {
        ErrorResponse error = new ErrorResponse(
                ex.getErrorCode(), ex.getMessage(), null, null, getRequestId(), Instant.now());
        return ResponseEntity.status(429).body(new Envelope(false, null, error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Envelope> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ErrorResponse error = new ErrorResponse(
                "INTERNAL_ERROR", "An internal error occurred. Please try again.", null, null, getRequestId(), Instant.now());
        return ResponseEntity.status(500).body(new Envelope(false, null, error));
    }

    private String getRequestId() {
        return MDC.get("requestId") != null ? MDC.get("requestId") : UUID.randomUUID().toString();
    }

    // -- Response DTOs --

    public record Envelope(boolean success, Object data, ErrorResponse error) {}

    public record ErrorResponse(
            String code,
            String message,
            Object details,
            Map<String, String> fieldErrors,
            String requestId,
            Instant timestamp
    ) {}
}
