package com.talentledger.application.port.inbound;

import com.talentledger.application.dto.request.LoginRequest;
import com.talentledger.application.dto.request.RegisterRequest;
import com.talentledger.application.dto.response.AuthResponse;
import com.talentledger.domain.auth.Credentials;

import java.util.UUID;

/**
 * Inbound port — Authentication use cases.
 * Implemented by AuthService in the application layer.
 */
public interface AuthUseCase {

    /** Native email/password registration. */
    AuthResponse register(RegisterRequest request);

    /** Native email/password login. */
    AuthResponse login(Credentials credentials, String clientIp, String userAgent);

    /** Exchange a Clerk JWT for our opaque session token (ADR-033, ADR-038). */
    AuthResponse exchangeClerkToken(String clerkJwt, String clientIp, String userAgent);

    /** Revoke a specific session. */
    void logout(UUID sessionId, UUID currentUserId);

    /** Revoke all sessions for a user. */
    void logoutAll(UUID userId);

    /** Request password reset email. */
    void requestPasswordReset(String email);

    /** Confirm password reset with token + new password. */
    void confirmPasswordReset(String token, String newPassword);

    /** Verify email with token. */
    void verifyEmail(String token);

    /** Resend the email verification link (rate-limited at the controller/filter level). */
    void resendVerificationEmail(String email);

    /**
     * Create an ephemeral guest account + session for the anonymous upload
     * preview flow (item #1). The guest gets a real session token and can use
     * every normal authenticated endpoint (upload, parse, search, CRUD) —
     * their data lives under this throwaway account until either claimed or
     * it expires (see ScheduledJobs.purgeExpiredGuestAccounts).
     */
    AuthResponse createGuestSession();

    /**
     * Called once a guest signs up/logs into a real account and chooses to
     * keep their data ("Save to Workspace"). Reassigns every dump the guest
     * uploaded to the now-authenticated real user, then deletes the guest
     * account. No-ops (does not throw) if guestUserId does not refer to an
     * actual, still-live guest account, so a stale/already-claimed/expired
     * guest session can't be replayed to hijack another user's data.
     */
    void claimGuestData(UUID guestUserId, UUID realUserId);
}
