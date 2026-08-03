package com.talentledger.application.service;

import com.talentledger.application.dto.request.RegisterRequest;
import com.talentledger.application.dto.response.AuthResponse;
import com.talentledger.application.port.inbound.AuthUseCase;
import com.talentledger.application.port.outbound.EmailSenderPort;
import com.talentledger.application.port.outbound.EmailSenderPort.EmailMessage;
import com.talentledger.domain.auth.AuthProviderPort;
import com.talentledger.domain.auth.Credentials;
import com.talentledger.domain.auth.Session;
import com.talentledger.domain.user.User;
import com.talentledger.domain.user.UserPlan;
import com.talentledger.domain.user.UserQuota;
import com.talentledger.domain.user.UserQuotaRepository;
import com.talentledger.domain.user.UserRepository;
import com.talentledger.domain.user.UserStatus;
import com.talentledger.infrastructure.persistence.entity.DataDumpEntity;
import com.talentledger.infrastructure.persistence.entity.EmailVerificationTokenEntity;
import com.talentledger.infrastructure.persistence.entity.PasswordResetTokenEntity;
import com.talentledger.infrastructure.persistence.repository.JpaDataDumpRepository;
import com.talentledger.infrastructure.persistence.repository.JpaEmailVerificationTokenRepository;
import com.talentledger.infrastructure.persistence.repository.JpaPasswordResetTokenRepository;
import com.talentledger.infrastructure.persistence.repository.JpaUserRepository;
import com.talentledger.shared.util.SessionTokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing AuthUseCase.
 * Orchestrates registration, login, Clerk exchange, session management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements AuthUseCase {

    private final UserRepository userRepository;
    private final JpaUserRepository jpaUserRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final AuthProviderPort authProviderPort;
    private final EmailSenderPort emailSenderPort;
    private final JpaEmailVerificationTokenRepository emailVerificationTokenRepository;
    private final JpaPasswordResetTokenRepository passwordResetTokenRepository;
    private final MfaService mfaService;
    private final com.talentledger.infrastructure.security.clerk.ClerkTokenVerifier clerkTokenVerifier;
    private final JpaDataDumpRepository jpaDataDumpRepository;
    private final com.talentledger.infrastructure.persistence.repository.ContactJpaRepository contactJpaRepository;

    @Value("${talentledger.demo.guest-storage-limit-bytes:104857600}")
    private long guestStorageLimitBytes;

    @Value("${talentledger.demo.ttl-days:7}")
    private int guestTtlDays;

    @Value("${talentledger.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private static final int EMAIL_VERIFICATION_TTL_HOURS = 24;
    private static final int PASSWORD_RESET_TTL_MINUTES = 30;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("AuthService.register start email={} nameLength={} acceptedTerms={}",
                request.email(), request.name() != null ? request.name().length() : 0, request.acceptedTerms());
        if (userRepository.existsByEmailExcludingDeleted(request.email())) {
            log.warn("AuthService.register duplicate email={}", request.email());
            throw new IllegalArgumentException("Email already registered");
        }
        if (!request.acceptedTerms()) {
            log.warn("AuthService.register rejected email={} reason=terms-not-accepted", request.email());
            throw new IllegalArgumentException("You must accept the Terms of Service and Privacy Policy");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        Instant now = Instant.now();
        User user = User.create(request.email(), b -> b.name(request.name())
                .passwordHash(passwordHash)
                .acceptedTermsAt(now)
                .acceptedPrivacyAt(now));
        user = userRepository.save(user);

        log.info("AuthService.register created userId={} email={}", user.getId(), user.getEmail());

        // Every user gets a quota row from day one — previously this was only
        // lazily created on first upload, so GET /me showed quota: null until then.
        userQuotaRepository.save(UserQuota.Builder.forUser(user.getId(), user.getPlan()).build());

        log.info("User registered: {}", user.getId());

        sendVerificationEmail(user);

        IssuedSession issued = createSessionForUser(user);
        return toAuthResponse(user, issued);
    }

    @Override
    public AuthResponse login(Credentials credentials, String clientIp, String userAgent) {
        User user = userRepository.findByEmail(credentials.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        // Reject non-active accounts before checking the password at all.
        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.SUSPENDED) {
            throw new com.talentledger.shared.exception.UnauthorizedException(
                    "ACCOUNT_" + user.getStatus().name(), "This account is no longer able to sign in");
        }
        if (user.isAccountLocked()) {
            throw new com.talentledger.shared.exception.UnauthorizedException(
                    "ACCOUNT_LOCKED", "Too many failed attempts. Try again later.");
        }

        if (!passwordEncoder.matches(credentials.getRawPassword(), user.getPasswordHash())) {
            // Per ADR/section 8.4: track failed attempts toward lockout/suspension.
            user.recordFailedLogin();
            userRepository.save(user);
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (user.isMfaEnabled() && user.getMfaSetupCompletedAt() != null) {
            if (!credentials.hasMfa()) {
                throw new com.talentledger.shared.exception.UnauthorizedException(
                        "MFA_REQUIRED", "This account requires a two-factor authentication code");
            }
            if (!mfaService.verifyMfaCode(user, credentials.getMfaCode())) {
                user.recordFailedLogin();
                userRepository.save(user);
                throw new com.talentledger.shared.exception.UnauthorizedException(
                        "MFA_INVALID", "Invalid two-factor authentication code");
            }
        }

        user.recordSuccessfulLogin(clientIp, userAgent);
        userRepository.save(user);

        log.info("User logged in: {}", user.getId());

        IssuedSession issued = createSessionForUser(user);
        return toAuthResponse(user, issued);
    }

    @Override
    @Transactional
    public AuthResponse exchangeClerkToken(String clerkJwt, String clientIp, String userAgent) {
        if (!clerkTokenVerifier.isConfigured()) {
            throw new com.talentledger.shared.exception.DomainException(
                    "NOT_IMPLEMENTED", "Clerk OAuth is not configured on this server", 501);
        }

        org.springframework.security.oauth2.jwt.Jwt jwt;
        try {
            jwt = clerkTokenVerifier.verify(clerkJwt);
        } catch (Exception e) {
            log.warn("Clerk token verification failed: {}", e.getMessage());
            throw new com.talentledger.shared.exception.UnauthorizedException(
                    "INVALID_CLERK_TOKEN", "Could not verify Clerk session");
        }

        String clerkId = jwt.getSubject();
        String email = firstNonBlank(jwt.getClaimAsString("email"), jwt.getClaimAsString("email_address"));
        if (clerkId == null || email == null) {
            throw new com.talentledger.shared.exception.UnauthorizedException(
                    "INVALID_CLERK_TOKEN", "Clerk token missing required claims");
        }

        User user = userRepository.findByClerkId(clerkId)
                .or(() -> userRepository.findByEmail(email))
                .orElseGet(() -> {
                    User created = User.create(email, b -> b.clerkId(clerkId).name(firstNonBlank(jwt.getClaimAsString("name"), email)));
                    created = userRepository.save(created);
                    userQuotaRepository.save(UserQuota.Builder.forUser(created.getId(), created.getPlan()).build());
                    return created;
                });

        if (user.getClerkId() == null) {
            user.linkClerkId(clerkId);
        }
        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.SUSPENDED) {
            throw new com.talentledger.shared.exception.UnauthorizedException(
                    "ACCOUNT_" + user.getStatus().name(), "This account is no longer able to sign in");
        }

        user.recordSuccessfulLogin(clientIp, userAgent);
        user = userRepository.save(user);

        log.info("User authenticated via Clerk: {}", user.getId());
        IssuedSession issued = createSessionForUser(user);
        return toAuthResponse(user, issued);
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    @Override
    public void logout(UUID sessionId, UUID currentUserId) {
        if (sessionId == null) {
            log.warn("Logout called with no resolvable session for user {}; revoking all sessions as a fallback", currentUserId);
            authProviderPort.revokeAllUserSessions(currentUserId, "Logout (session id unavailable)");
            return;
        }
        authProviderPort.revokeSession(sessionId, "User logout");
        log.info("Session revoked: {}", sessionId);
    }

    @Override
    public void logoutAll(UUID userId) {
        authProviderPort.revokeAllUserSessions(userId, "Logout all");
        log.info("All sessions revoked for user: {}", userId);
    }

    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            // ADR-047: invalidate previous unused tokens before creating a new one
            passwordResetTokenRepository.deleteByUserIdAndUsedAtIsNull(user.getId());

            String rawToken = SessionTokenUtils.generateToken();
            String tokenHash = SessionTokenUtils.hash(rawToken);
            Instant expiresAt = Instant.now().plus(PASSWORD_RESET_TTL_MINUTES, ChronoUnit.MINUTES);

            PasswordResetTokenEntity entity = PasswordResetTokenEntity.builder()
                    .user(jpaUserRepository.getReferenceById(user.getId()))
                    .tokenHash(tokenHash)
                    .expiresAt(expiresAt)
                    .createdAt(Instant.now())
                    .build();
            passwordResetTokenRepository.save(entity);

            String resetLink = frontendUrl + "/reset-password?token=" + rawToken;
            trySend(user.getEmail(), "Reset your TalentLedger password",
                    "Click the link to reset your password (expires in " + PASSWORD_RESET_TTL_MINUTES + " minutes): " + resetLink);

            log.info("Password reset token issued for user {}", user.getId());
        });
        // Deliberately no else-branch / no differing behavior for unknown emails —
        // same response either way, to avoid leaking which emails are registered.
    }

    @Override
    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        String tokenHash = SessionTokenUtils.hash(token);
        PasswordResetTokenEntity entity = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(tokenHash, Instant.now())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        User user = userRepository.findById(entity.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        entity.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(entity);

        // A password reset is a strong signal of compromise or a fresh, trusted
        // recovery — either way, kill all existing sessions so a stolen session
        // token from before the reset can't keep working.
        authProviderPort.revokeAllUserSessions(user.getId(), "Password reset");

        log.info("Password reset completed for user {}", user.getId());
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        String tokenHash = SessionTokenUtils.hash(token);
        EmailVerificationTokenEntity entity = emailVerificationTokenRepository
                .findByTokenHashAndVerifiedAtIsNullAndExpiresAtAfter(tokenHash, Instant.now())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));

        User user = userRepository.findById(entity.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));

        user.markEmailVerified();
        userRepository.save(user);

        entity.setVerifiedAt(Instant.now());
        emailVerificationTokenRepository.save(entity);

        log.info("Email verified for user {}", user.getId());
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        userRepository.findByEmail(email)
                .filter(u -> !u.isEmailVerified())
                .ifPresent(this::sendVerificationEmail);
        // Same non-committal behavior regardless of whether the email exists
        // or is already verified — avoids leaking account existence/state.
    }

    @Override
    @Transactional
    public AuthResponse createGuestSession() {
        // Real (but ephemeral) User row — see field docs on User.isGuest.
        // Synthetic, guaranteed-unique email; guests never see or use it.
        String guestEmail = "guest-" + UUID.randomUUID() + "@guest.talentledger.internal";
        Instant expiresAt = Instant.now().plus(guestTtlDays, ChronoUnit.DAYS);

        User guest = User.create(guestEmail, b -> b
                .name("Guest")
                .plan(UserPlan.FREE)
                .isGuest(true)
                .guestExpiresAt(expiresAt));
        guest = userRepository.save(guest);

        // Guests get the normal FREE plan's dump/upload/contact limits — those
        // only bite once they confirm-save a dump anyway (DumpService). The
        // one limit that matters *during* free exploration is the storage
        // byte cap, which we override to the guest-specific 100MB ceiling
        // from the architecture doc rather than FREE's general default.
        UserQuota quota = new UserQuota.Builder(
                UserQuota.Builder.forUser(guest.getId(), UserPlan.FREE).build())
                .storageBytesLimit(guestStorageLimitBytes)
                .build();
        userQuotaRepository.save(quota);

        log.info("Guest session created: userId={} expiresAt={}", guest.getId(), expiresAt);

        IssuedSession issued = createSessionForUser(guest);
        return toAuthResponse(guest, issued);
    }

    @Override
    @Transactional
    public void claimGuestData(UUID guestUserId, UUID realUserId) {
        User guest = userRepository.findById(guestUserId).orElse(null);
        // Refuse silently rather than throwing: a stale, already-claimed, or
        // expired guestUserId must not be usable to attach arbitrary data to
        // someone else's account, and the caller (AuthController) shouldn't
        // surface an error for what's usually just a double-click.
        if (guest == null || !guest.isGuest()) {
            log.warn("claimGuestData: {} is not a live guest account — ignoring", guestUserId);
            return;
        }
        if (guestUserId.equals(realUserId)) {
            return;
        }

        List<DataDumpEntity> dumps = jpaDataDumpRepository.findByUserIdOrderByCreatedAtDesc(guestUserId);
        for (DataDumpEntity dump : dumps) {
            dump.setUserId(realUserId);
            dump.setUpdatedAt(Instant.now());
            jpaDataDumpRepository.save(dump);
        }
        contactJpaRepository.reassignOwner(guestUserId, realUserId);

        jpaUserRepository.deleteById(guestUserId);

        log.info("Claimed {} dump(s) from guest {} onto user {}", dumps.size(), guestUserId, realUserId);
    }

    private void sendVerificationEmail(User user) {
        String rawToken = SessionTokenUtils.generateToken();
        String tokenHash = SessionTokenUtils.hash(rawToken);
        Instant expiresAt = Instant.now().plus(EMAIL_VERIFICATION_TTL_HOURS, ChronoUnit.HOURS);

        EmailVerificationTokenEntity entity = EmailVerificationTokenEntity.builder()
                .user(jpaUserRepository.getReferenceById(user.getId()))
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .build();
        emailVerificationTokenRepository.save(entity);

        String verifyLink = frontendUrl + "/verify-email?token=" + rawToken;
        trySend(user.getEmail(), "Verify your TalentLedger email",
                "Click the link to verify your email (expires in " + EMAIL_VERIFICATION_TTL_HOURS + " hours): " + verifyLink);
    }

    /**
     * Send an email without letting a mail-provider failure break the calling
     * transaction (e.g. registration should still succeed even if the mail
     * provider is briefly down — the user can always hit "resend").
     */
    private void trySend(String to, String subject, String textBody) {
        try {
            emailSenderPort.send(new EmailMessage("noreply@talentledger.app", to, subject, null, textBody));
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private IssuedSession createSessionForUser(User user) {
        String rawToken = SessionTokenUtils.generateToken();
        String tokenHash = SessionTokenUtils.hash(rawToken);
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        Session session = authProviderPort.createSession(user.getId(), tokenHash, expiresAt);
        return new IssuedSession(session, rawToken);
    }

    /**
     * Carries a freshly-created session together with its one-time-visible raw token.
     * Only the SHA-256 hash of {@code rawToken} is ever persisted (see {@link Session}).
     */
    private record IssuedSession(Session session, String rawToken) {
    }

    private AuthResponse toAuthResponse(User user, IssuedSession issued) {
        return new AuthResponse(
                issued.rawToken(),
                user.getId(),
                user.getEmail(),
                user.getName() != null ? user.getName() : user.getEmail().split("@")[0],
                user.getRole().name(),
                user.getPlan().name(),
                user.isEmailVerified(),
                user.isMfaEnabled(),
                user.isGuest(),
                issued.session().getExpiresAt()
        );
    }
}
