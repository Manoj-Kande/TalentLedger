package com.talentledger.domain.auth;

import com.talentledger.domain.shared.BusinessRule;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Standalone entity representing an authenticated user session.
 *
 * <p>Does NOT extend {@link com.talentledger.domain.shared.AggregateRoot} because
 * sessions do not raise domain events — they are a supporting entity.
 */
public class Session {

    private UUID id;
    private UUID userId;
    private String sessionTokenHash;
    private String clerkSessionId;
    private String deviceName;
    private String deviceType;
    private String browser;
    private String os;
    private String ipAddress;
    private String userAgent;
    private String deviceFingerprint;
    private String countryCode;
    private String city;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastActiveAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private String revokeReason;
    private boolean isTrusted;
    private Instant trustedUntil;
    private boolean isImpersonation;
    private UUID impersonatedByAdminId;
    private String impersonationReason;

    protected Session() {
        // for infrastructure reconstitution
    }

    private Session(UUID id,
                    UUID userId,
                    String sessionTokenHash,
                    String clerkSessionId,
                    String deviceName,
                    String deviceType,
                    String browser,
                    String os,
                    String ipAddress,
                    String userAgent,
                    String deviceFingerprint,
                    String countryCode,
                    String city,
                    Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.sessionTokenHash = sessionTokenHash;
        this.clerkSessionId = clerkSessionId;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.browser = browser;
        this.os = os;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceFingerprint = deviceFingerprint;
        this.countryCode = countryCode;
        this.city = city;
        this.expiresAt = expiresAt;
        this.isTrusted = false;
        this.isImpersonation = false;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastActiveAt = now;
    }

    /**
     * Factory method to create a new session.
     *
     * @param userId            the owning user (required)
     * @param sessionTokenHash  SHA-256 hash of the opaque session token (required, unique)
     * @param expiresAt         when this session expires (required)
     * @param clerkSessionId    optional Clerk session ID for Clerk-linked sessions
     * @param deviceName        human-readable device name
     * @param deviceType        e.g. "desktop", "mobile", "tablet"
     * @param browser           browser name/version
     * @param os                operating system
     * @param ipAddress         client IP address
     * @param userAgent         full User-Agent header
     * @param deviceFingerprint anonymized device fingerprint (max 64 chars)
     * @param countryCode       ISO 3166-1 alpha-2 country code (max 2 chars)
     * @param city              city name
     * @return a new Session instance
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException if required fields are invalid
     */
    public static Session create(UUID userId,
                                  String sessionTokenHash,
                                  Instant expiresAt,
                                  String clerkSessionId,
                                  String deviceName,
                                  String deviceType,
                                  String browser,
                                  String os,
                                  String ipAddress,
                                  String userAgent,
                                  String deviceFingerprint,
                                  String countryCode,
                                  String city) {
        BusinessRule.ensure(userId != null, "User ID must not be null");
        BusinessRule.ensure(sessionTokenHash != null && !sessionTokenHash.isBlank(),
                "Session token hash must not be blank");
        BusinessRule.ensure(expiresAt != null, "Expires-at must not be null");
        BusinessRule.ensure(deviceFingerprint == null || deviceFingerprint.length() <= 64,
                "Device fingerprint must not exceed 64 characters");
        BusinessRule.ensure(countryCode == null || countryCode.length() <= 2,
                "Country code must not exceed 2 characters");

        return new Session(
                UUID.randomUUID(),
                userId,
                sessionTokenHash,
                clerkSessionId,
                deviceName,
                deviceType,
                browser,
                os,
                ipAddress,
                userAgent,
                deviceFingerprint,
                countryCode,
                city,
                expiresAt
        );
    }

    // ── Domain Behaviour ──────────────────────────────────

    /**
     * @return true if this session has passed its expiration time
     */
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    /**
     * @return true if this session has been explicitly revoked
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * @return true if the session is both not expired and not revoked
     */
    public boolean isActive() {
        return !isExpired() && !isRevoked();
    }

    /**
     * Revoke this session with a reason.
     *
     * @param reason human-readable reason for revocation
     */
    public void revoke(String reason) {
        this.revokedAt = Instant.now();
        this.revokeReason = reason;
        this.updatedAt = Instant.now();
    }

    /**
     * Mark this session as a trusted device for the given duration.
     *
     * @param trustDuration how long the trust lasts
     */
    public void markTrusted(Duration trustDuration) {
        this.isTrusted = true;
        this.trustedUntil = Instant.now().plus(trustDuration);
        this.updatedAt = Instant.now();
    }

    /**
     * Update the last-active timestamp to now.
     */
    public void touch() {
        this.lastActiveAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ── Getters ────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getSessionTokenHash() { return sessionTokenHash; }
    public String getClerkSessionId() { return clerkSessionId; }
    public String getDeviceName() { return deviceName; }
    public String getDeviceType() { return deviceType; }
    public String getBrowser() { return browser; }
    public String getOs() { return os; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public String getCountryCode() { return countryCode; }
    public String getCity() { return city; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public boolean isTrusted() { return isTrusted; }
    public Instant getTrustedUntil() { return trustedUntil; }
    public boolean isImpersonation() { return isImpersonation; }
    public UUID getImpersonatedByAdminId() { return impersonatedByAdminId; }
    public String getImpersonationReason() { return impersonationReason; }

    // ── Setters (for infrastructure / admin actions) ──────

    public void setClerkSessionId(String clerkSessionId) {
        this.clerkSessionId = clerkSessionId;
        this.updatedAt = Instant.now();
    }

    public void setImpersonation(boolean isImpersonation, UUID adminId, String reason) {
        this.isImpersonation = isImpersonation;
        this.impersonatedByAdminId = adminId;
        this.impersonationReason = reason;
        this.updatedAt = Instant.now();
    }
}
