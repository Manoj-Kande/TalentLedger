package com.talentledger.infrastructure.web.controller;

import com.talentledger.application.dto.request.LoginRequest;
import com.talentledger.application.dto.request.MfaVerifyRequest;
import com.talentledger.application.dto.request.PasswordResetConfirmRequest;
import com.talentledger.application.dto.request.PasswordResetRequest;
import com.talentledger.application.dto.request.RegisterRequest;
import com.talentledger.application.dto.response.AuthResponse;
import com.talentledger.application.port.inbound.AuthUseCase;
import com.talentledger.application.service.MfaService;
import com.talentledger.domain.auth.Credentials;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Auth Controller — handles login, register, logout, password reset, Clerk exchange, MFA.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthUseCase authUseCase;
    private final MfaService mfaService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("AuthController.register start email={} nameLength={} acceptedTerms={}",
                request.email(), request.name() != null ? request.name().length() : 0, request.acceptedTerms());
        AuthResponse response = authUseCase.register(request);
        log.info("AuthController.register success email={}", request.email());
        return ok(toAuthEnvelope(response));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest) {
        Credentials credentials = Credentials.of(request.email(), request.password(), request.mfaCode());
        AuthResponse response = authUseCase.login(credentials,
                getClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ok(toAuthEnvelope(response));
    }

    @PostMapping("/exchange")
    public ResponseEntity<Map<String, Object>> exchangeClerkToken(@RequestHeader("Authorization") String clerkJwt,
                                                                  HttpServletRequest httpRequest) {
        AuthResponse response = authUseCase.exchangeClerkToken(clerkJwt.replace("Bearer ", ""),
                getClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        return ok(toAuthEnvelope(response));
    }

    // Item #1: anonymous free-preview flow. Public — no session required to call this.
    @PostMapping("/guest")
    public ResponseEntity<Map<String, Object>> createGuestSession() {
        AuthResponse response = authUseCase.createGuestSession();
        return ok(toAuthEnvelope(response));
    }

    // Called right after the guest completes a real register/login, while the
    // frontend still holds the (now-stale) guest session token client-side.
    // Requires the caller to already be authenticated as the REAL account —
    // guestUserId alone is not sufficient authorization to move data.
    @PostMapping("/guest/claim")
    public ResponseEntity<Map<String, Object>> claimGuestData(@RequestBody Map<String, String> body,
                                                                HttpServletRequest httpRequest) {
        UUID realUserId = getCurrentUserId(httpRequest);
        UUID guestUserId = UUID.fromString(body.get("guestUserId"));
        authUseCase.claimGuestData(guestUserId, realUserId);
        return ok(Map.of("message", "Data claimed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest httpRequest) {
        Object sessionIdAttr = httpRequest.getAttribute("sessionId");
        UUID sessionId = sessionIdAttr != null ? UUID.fromString(sessionIdAttr.toString()) : null;
        authUseCase.logout(sessionId, getCurrentUserId(httpRequest));
        return ok(Map.of("message", "Logged out successfully"));
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Map<String, Object>> logoutAll(HttpServletRequest httpRequest) {
        authUseCase.logoutAll(getCurrentUserId(httpRequest));
        return ok(Map.of("message", "All sessions revoked"));
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Map<String, Object>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authUseCase.requestPasswordReset(request.email());
        return ok(Map.of("message", "If an account exists, a password reset email has been sent."));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Map<String, Object>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authUseCase.confirmPasswordReset(request.token(), request.newPassword());
        return ok(Map.of("message", "Password reset successfully."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(@RequestBody Map<String, String> body) {
        authUseCase.verifyEmail(body.get("token"));
        return ok(Map.of("message", "Email verified successfully."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, Object>> resendVerification(@RequestBody Map<String, String> body) {
        authUseCase.resendVerificationEmail(body.get("email"));
        return ok(Map.of("message", "If an account exists and isn't already verified, a new verification email has been sent."));
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<Map<String, Object>> mfaSetup(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = mfaService.beginSetup(userId);
        return ok(Map.of(
                "secretKey", result.secretKey(),
                "qrCodeUrl", result.qrCodeUrl(),
                "message", "Scan the QR code (or enter the secret manually), then confirm with a 6-digit code via /mfa/verify."
        ));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<Map<String, Object>> mfaVerify(@Valid @RequestBody MfaVerifyRequest request,
                                                          HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = mfaService.confirmSetup(userId, request.code());
        return ok(Map.of(
                "backupCodes", result.backupCodes(),
                "message", "Two-factor authentication is now active. Save these backup codes somewhere safe — they will not be shown again."
        ));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<Map<String, Object>> mfaDisable(@Valid @RequestBody MfaVerifyRequest request,
                                                           HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        mfaService.disable(userId, request.code());
        return ok(Map.of("message", "Two-factor authentication has been disabled."));
    }

    @PostMapping("/mfa/backup-codes")
    public ResponseEntity<Map<String, Object>> mfaRegenerateBackupCodes(@Valid @RequestBody MfaVerifyRequest request,
                                                                        HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = mfaService.regenerateBackupCodes(userId, request.code());
        return ok(Map.of(
                "backupCodes", result.backupCodes(),
                "message", "New backup codes generated. Your old codes no longer work."
        ));
    }

    // Clerk webhook handler
    @PostMapping("/clerk")
    public ResponseEntity<Map<String, Object>> clerkWebhook(@RequestBody String payload,
                                                               @RequestHeader("svix-id") String messageId,
                                                               @RequestHeader("svix-timestamp") String timestamp,
                                                               @RequestHeader("svix-signature") String signature) {
        return ok(Map.of("received", true));
    }

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    /**
     * Wrap AuthResponse into the format the frontend expects:
     * { user: { id, email, name, role, plan, ... }, sessionToken: "..." }
     */
    private Map<String, Object> toAuthEnvelope(AuthResponse r) {
        Map<String, Object> user = Map.of(
                "id", r.userId().toString(),
                "email", r.email(),
                "name", r.name(),
                "role", r.role(),
                "plan", r.plan(),
                "isActive", true,
                "isGuest", r.isGuest(),
                "emailVerified", r.emailVerified(),
                "createdAt", java.time.Instant.now().toString(),
                "updatedAt", java.time.Instant.now().toString()
        );
        return Map.of(
                "user", user,
                "sessionToken", r.sessionToken()
        );
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private UUID getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new com.talentledger.shared.exception.UnauthorizedException(
                    "No authenticated user on request (SessionAuthFilter should have rejected this earlier)");
        }
        return UUID.fromString(userId.toString());
    }
}
