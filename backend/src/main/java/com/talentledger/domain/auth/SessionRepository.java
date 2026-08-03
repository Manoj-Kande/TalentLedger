package com.talentledger.domain.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and retrieving {@link Session} entities.
 */
public interface SessionRepository {

    Optional<Session> findByTokenHash(String tokenHash);

    Optional<Session> findById(UUID id);

    Session save(Session session);

    List<Session> findByUserIdAndRevokedAtIsNull(UUID userId);

    int countByUserIdAndRevokedAtIsNull(UUID userId);

    void delete(Session session);
}
