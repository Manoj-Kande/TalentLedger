package com.talentledger.application.port.inbound;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound port — Admin use cases.
 * Implemented by AdminService in the application layer.
 * All methods require ADMIN role.
 */
public interface AdminUseCase {

    /** List all users with pagination. */
    AdminResult listUsers(int page, int size, String status, String search);

    /** Get detailed user info. */
    AdminResult getUserDetail(UUID userId);

    /** Ban a user with reason. */
    AdminResult banUser(UUID adminUserId, UUID userId, String reason);

    /** Unban a user. */
    AdminResult unbanUser(UUID adminUserId, UUID userId, String reason);

    /**
     * Change a user's role and/or plan (e.g. granting Premium access
     * without a payment provider). Either parameter may be null to leave
     * that axis unchanged. Also resyncs the user's quota limits to match
     * the new plan when the plan changes, preserving current usage counts.
     */
    AdminResult updateUserAccess(UUID adminUserId, UUID userId,
                                  com.talentledger.domain.user.UserRole newRole,
                                  com.talentledger.domain.user.UserPlan newPlan,
                                  String reason);

    /** Impersonate a user (30-min session). */
    AdminResult impersonateUser(UUID adminUserId, UUID targetUserId, String reason);

    /** Get platform-wide stats. */
    AdminResult getPlatformStats();

    /** Get audit log entries. */
    AdminResult getAuditLog(UUID userId, int limit);

    /** Update a system config key. */
    AdminResult updateConfig(String key, Object value, UUID adminUserId);

    /** Get all system configs. */
    AdminResult getConfigs();

    // ── Inner types ──────────────────────────────────────

    sealed interface AdminResult permits Success, NotFound, Forbidden, Error {}
    record Success(Object data) implements AdminResult {}
    record NotFound(String message) implements AdminResult {}
    record Forbidden(String message) implements AdminResult {}
    record Error(String code, String message) implements AdminResult {}
}
