package com.talentledger.application.service;

import com.talentledger.domain.auth.MfaSecret;
import com.talentledger.domain.user.User;
import com.talentledger.domain.user.UserPlan;
import com.talentledger.domain.user.UserRepository;
import com.talentledger.infrastructure.persistence.entity.MfaBackupCodeEntity;
import com.talentledger.infrastructure.persistence.repository.JpaMfaBackupCodeRepository;
import com.talentledger.infrastructure.persistence.repository.JpaUserRepository;
import com.talentledger.infrastructure.security.mfa.MfaEncryptionService;
import com.talentledger.infrastructure.security.mfa.TotpService;
import com.talentledger.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Premium-only TOTP two-factor authentication: setup, verification,
 * disable, and backup-code management.
 *
 * <p>Gated on {@code plan != FREE} per the tier table (section 3 of the
 * master architecture doc: 2FA is "No" for Free, "Yes" for Pro/Team).
 *
 * <p>Two-stage enable per the {@code User} domain model: {@code enableMfa()}
 * flips {@code mfaEnabled} immediately but leaves {@code mfaSetupCompletedAt}
 * null until the user proves they can generate a valid code via
 * {@code completeMfaSetup()}. Login only *requires* a code once both are
 * set — otherwise a user who started setup but never finished (e.g. lost
 * the QR code) could lock themselves out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    private final UserRepository userRepository;
    private final JpaUserRepository jpaUserRepository;
    private final JpaMfaBackupCodeRepository backupCodeRepository;
    private final MfaEncryptionService encryptionService;
    private final TotpService totpService;

    public record MfaSetupResult(String secretKey, String qrCodeUrl) {}
    public record MfaVerifyResult(List<String> backupCodes) {}

    @Transactional
    public MfaSetupResult beginSetup(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        requirePaidPlan(user);

        MfaSecret secret = MfaSecret.generate(user.getEmail());
        String encryptedSecret = encryptionService.encrypt(secret.getSecretKey());

        user.enableMfa("TOTP", encryptedSecret);
        userRepository.save(user);

        log.info("MFA setup started for user {}", userId);
        // secretKey is shown to the user exactly once here (and via the QR code) —
        // it is never retrievable again in plaintext after this response.
        return new MfaSetupResult(secret.getSecretKey(), secret.getQrCodeUrl());
    }

    @Transactional
    public MfaVerifyResult confirmSetup(UUID userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (!user.isMfaEnabled() || user.getMfaSecretEncrypted() == null) {
            throw new IllegalStateException("MFA setup was not started for this user");
        }

        String secretKey = encryptionService.decrypt(user.getMfaSecretEncrypted());
        if (!totpService.verifyCode(secretKey, code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }

        // Clear out any stale codes from a previous setup attempt, then issue fresh ones.
        backupCodeRepository.deleteByUserId(userId);
        List<String> rawBackupCodes = MfaSecret.generateBackupCodes();
        persistBackupCodes(userId, rawBackupCodes);

        user.completeMfaSetup(rawBackupCodes.size());
        userRepository.save(user);

        log.info("MFA setup completed for user {}", userId);
        return new MfaVerifyResult(rawBackupCodes);
    }

    @Transactional
    public void disable(UUID userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        requireValidMfaCodeOrLockout(user, code);

        backupCodeRepository.deleteByUserId(userId);
        user.disableMfa();
        userRepository.save(user);

        log.info("MFA disabled for user {}", userId);
    }

    @Transactional
    public MfaVerifyResult regenerateBackupCodes(UUID userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        requireValidMfaCodeOrLockout(user, code);

        backupCodeRepository.deleteByUserId(userId);
        List<String> rawBackupCodes = MfaSecret.generateBackupCodes();
        persistBackupCodes(userId, rawBackupCodes);

        user.regenerateBackupCodes(rawBackupCodes.size());
        userRepository.save(user);

        log.info("MFA backup codes regenerated for user {}", userId);
        return new MfaVerifyResult(rawBackupCodes);
    }

    /**
     * Verify a code presented at login time — tries TOTP first, then falls
     * back to matching it against an unused backup code. Only call this when
     * {@code user.isMfaEnabled() && user.getMfaSetupCompletedAt() != null}.
     */
    @Transactional
    public boolean verifyMfaCode(User user, String submittedCode) {
        if (submittedCode == null || submittedCode.isBlank()) {
            return false;
        }

        if (user.getMfaSecretEncrypted() != null) {
            String secretKey = encryptionService.decrypt(user.getMfaSecretEncrypted());
            if (totpService.verifyCode(secretKey, submittedCode)) {
                return true;
            }
        }

        // Not a valid TOTP code — try it as a backup code.
        String candidateHash = sha256Hex(submittedCode.trim());
        return backupCodeRepository.findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), candidateHash)
                .map(entity -> {
                    entity.setUsedAt(Instant.now());
                    backupCodeRepository.save(entity);
                    user.consumeBackupCode();
                    userRepository.save(user);
                    log.info("Backup code consumed for user {}; {} remaining", user.getId(), user.getMfaBackupCodesRemaining());
                    return true;
                })
                .orElse(false);
    }

    /**
     * Verify an MFA code for a sensitive, already-authenticated action
     * (disable / regenerate backup codes), with the same lockout tracking
     * as login. Without this, a stolen session token would let an attacker
     * grind through 6-digit codes with only the generic per-user rate limit
     * (30–300/min) standing in the way — far more attempts than the 5/10/20
     * lockout thresholds allow for password/login guessing.
     */
    private void requireValidMfaCodeOrLockout(User user, String code) {
        if (user.isAccountLocked()) {
            throw new UnauthorizedException("ACCOUNT_LOCKED", "Too many failed attempts. Try again later.");
        }
        if (!verifyMfaCode(user, code)) {
            user.recordFailedLogin();
            userRepository.save(user);
            throw new UnauthorizedException("MFA_INVALID", "Invalid MFA code");
        }
    }

    private void persistBackupCodes(UUID userId, List<String> rawCodes) {
        var userRef = jpaUserRepository.getReferenceById(userId);
        for (String rawCode : rawCodes) {
            String hash = sha256Hex(rawCode);
            String hint = rawCode.substring(Math.max(0, rawCode.length() - 4));
            backupCodeRepository.save(MfaBackupCodeEntity.builder()
                    .user(userRef)
                    .codeHash(hash)
                    .codeHint(hint)
                    .createdAt(Instant.now())
                    .build());
        }
    }

    private void requirePaidPlan(User user) {
        if (user.getPlan() == UserPlan.FREE) {
            throw new UnauthorizedException("PLAN_REQUIRED", "Two-factor authentication is available on Pro and Team plans");
        }
    }

    /** Backup codes are random, high-entropy, single-use — a fast SHA-256 hash is appropriate (no bcrypt needed). */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
