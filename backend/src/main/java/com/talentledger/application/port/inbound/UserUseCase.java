package com.talentledger.application.port.inbound;

import com.talentledger.domain.user.User;
import com.talentledger.domain.user.UserQuota;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound port — User & profile use cases.
 * Implemented by UserSessionService in the application layer.
 */
public interface UserUseCase {

    /** Get current user profile with quotas. */
    UserWithQuota getCurrentUser(UUID userId);

    /** Get active sessions for the user. */
    List<SessionInfo> getActiveSessions(UUID userId);

    /** Revoke a specific session. */
    void revokeSession(UUID sessionId, UUID userId);

    /** Get login history (last 50). */
    List<LoginHistoryEntry> getLoginHistory(UUID userId);

    /** Get trusted devices. */
    List<DeviceInfo> getDevices(UUID userId);

    /** Revoke a device. */
    void revokeDevice(UUID deviceId, UUID userId);

    /** Update user profile (name, timezone, locale). */
    UserWithQuota updateProfile(UUID userId, String name, String timezone, String locale);

    /** Update onboarding status. */
    void updateOnboarding(UUID userId, boolean completed, java.util.Map<String, Object> profile);

    /** Get dashboard stats for current user. */
    UserStats getUserStats(UUID userId);

    /** Get user's activity feed (last 50 actions). */
    List<ActivityEntry> getActivity(UUID userId);

    // ── Inner types ──────────────────────────────────────

    record UserWithQuota(User user, UserQuota quota) {}

    record SessionInfo(
        UUID id,
        String deviceName,
        String deviceType,
        String browser,
        String os,
        String ipAddress,
        String countryCode,
        boolean isTrusted,
        boolean isImpersonation,
        java.time.Instant lastActiveAt,
        java.time.Instant expiresAt,
        java.time.Instant createdAt
    ) {}

    record LoginHistoryEntry(
        UUID id,
        String attemptType,
        boolean success,
        String failureReason,
        String ipAddress,
        String userAgent,
        java.time.Instant createdAt
    ) {}

    record DeviceInfo(
        UUID id,
        String deviceName,
        String deviceType,
        String browser,
        String os,
        boolean isTrusted,
        boolean is2faTrusted,
        java.time.Instant lastSeenAt,
        java.time.Instant firstSeenAt
    ) {}

    record UserStats(
        long totalContacts,
        long totalCompanies,
        long totalDumps,
        long uploadsThisMonth,
        long storageUsedBytes,
        long storageLimitBytes,
        long contactsLimit,
        List<ContactCompanyStat> topCompanies
    ) {}

    record ContactCompanyStat(String companyName, long count) {}

    record ActivityEntry(
        String action,
        String targetType,
        String targetName,
        java.time.Instant createdAt
    ) {}
}
