package com.talentledger.infrastructure.adapter.outbound;

import com.talentledger.domain.auth.AuthProviderPort;
import com.talentledger.domain.auth.Session;
import com.talentledger.domain.auth.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Infrastructure adapter implementing AuthProviderPort.
 * Bridges the domain session port to the SessionRepository.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthProviderAdapter implements AuthProviderPort {

    private final SessionRepository sessionRepository;

    @Override
    public Optional<Session> validateAndGetSession(String tokenHash) {
        return sessionRepository.findByTokenHash(tokenHash)
                .filter(Session::isActive);
    }

    @Override
    public Session createSession(UUID userId, String tokenHash, Instant expiresAt) {
        Session session = Session.create(
                userId, tokenHash, expiresAt,
                null, null, null, null, null, null, null, null, null, null
        );
        return sessionRepository.save(session);
    }

    @Override
    public Session createImpersonationSession(UUID targetUserId, UUID adminUserId, String tokenHash,
                                               Instant expiresAt, String reason) {
        Session session = Session.create(
                targetUserId, tokenHash, expiresAt,
                null, null, null, null, null, null, null, null, null, null
        );
        session.setImpersonation(true, adminUserId, reason);
        return sessionRepository.save(session);
    }

    @Override
    public void revokeSession(UUID sessionId, String reason) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.revoke(reason);
            sessionRepository.save(session);
            log.debug("Session {} revoked: {}", sessionId, reason);
        });
    }

    @Override
    public void revokeAllUserSessions(UUID userId, String reason) {
        List<Session> activeSessions = sessionRepository.findByUserIdAndRevokedAtIsNull(userId);
        activeSessions.forEach(session -> {
            session.revoke(reason);
            sessionRepository.save(session);
        });
        log.debug("All {} sessions revoked for user: {}", activeSessions.size(), userId);
    }

    @Override
    public List<Session> findActiveSessionsByUserId(UUID userId) {
        return sessionRepository.findByUserIdAndRevokedAtIsNull(userId).stream()
                .filter(Session::isActive)
                .toList();
    }

    @Override
    public int countActiveSessionsByUserId(UUID userId) {
        return (int) sessionRepository.findByUserIdAndRevokedAtIsNull(userId).stream()
                .filter(Session::isActive)
                .count();
    }
}
