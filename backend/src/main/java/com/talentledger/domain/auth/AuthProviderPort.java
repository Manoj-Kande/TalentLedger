package com.talentledger.domain.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for session lifecycle operations.
 *
 * <p>This is the primary port through which the application layer
 * interacts with session management. Implementations live in the
 * infrastructure adapter.
 */
public interface AuthProviderPort {

    /**
     * Look up the active session for the given token hash.
     *
     * @param tokenHash SHA-256 hash of the opaque session token
     * @return the session if found, active, and not revoked
     */
    Optional<Session> validateAndGetSession(String tokenHash);

    /**
     * Create and persist a new session.
     *
     * @param userId     the owning user
     * @param tokenHash  SHA-256 hash of the opaque session token
     * @param expiresAt  when this session expires
     * @return the persisted session
     */
    Session createSession(UUID userId, String tokenHash, Instant expiresAt);

    /**
     * Create and persist an admin impersonation session for a target user.
     * Per ADR/section 8.2: short-lived (typically 30 min), always flagged
     * and reason-tracked for audit purposes.
     *
     * @param targetUserId the user being impersonated
     * @param adminUserId  the admin performing the impersonation
     * @param tokenHash    SHA-256 hash of the opaque session token
     * @param expiresAt    when this session expires (should be short, e.g. now + 30 min)
     * @param reason       required justification, persisted with the session
     * @return the persisted, impersonation-flagged session
     */
    Session createImpersonationSession(UUID targetUserId, UUID adminUserId, String tokenHash, Instant expiresAt, String reason);

    /**
     * Revoke a specific session.
     *
     * @param sessionId the session to revoke
     * @param reason    human-readable reason
     */
    void revokeSession(UUID sessionId, String reason);

    /**
     * Revoke all active sessions for a user (e.g. on password change).
     *
     * @param userId the user whose sessions should be revoked
     * @param reason human-readable reason
     */
    void revokeAllUserSessions(UUID userId, String reason);

    /**
     * Find all active (non-expired, non-revoked) sessions for a user.
     *
     * @param userId the owning user
     * @return list of active sessions
     */
    List<Session> findActiveSessionsByUserId(UUID userId);

    /**
     * Count the number of active sessions for a user.
     *
     * @param userId the owning user
     * @return count of active sessions
     */
    int countActiveSessionsByUserId(UUID userId);
}
