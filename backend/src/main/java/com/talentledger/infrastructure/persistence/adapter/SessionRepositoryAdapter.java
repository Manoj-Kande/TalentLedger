package com.talentledger.infrastructure.persistence.adapter;

import com.talentledger.domain.auth.Session;
import com.talentledger.domain.auth.SessionRepository;
import com.talentledger.infrastructure.persistence.entity.UserEntity;
import com.talentledger.infrastructure.persistence.entity.UserSessionEntity;
import com.talentledger.infrastructure.persistence.repository.JpaSessionRepository;
import com.talentledger.infrastructure.persistence.repository.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that bridges the {@link SessionRepository} domain port
 * to the Spring Data JPA {@link com.talentledger.infrastructure.persistence.repository.JpaSessionRepository}.
 *
 * <p>{@link Session} has a protected no-arg constructor for infrastructure
 * reconstitution, but it resides in a different package, so reflection is used.
 */
@Component
@RequiredArgsConstructor
public class SessionRepositoryAdapter implements SessionRepository {

    private final JpaSessionRepository jpaRepository;
    private final JpaUserRepository userJpaRepository;

    // ── Query methods ────────────────────────────────────────────

    @Override
    public Optional<Session> findByTokenHash(String tokenHash) {
        return jpaRepository.findBySessionTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public Optional<Session> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Session> findByUserIdAndRevokedAtIsNull(UUID userId) {
        return jpaRepository.findByUserIdAndRevokedAtIsNull(userId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int countByUserIdAndRevokedAtIsNull(UUID userId) {
        return jpaRepository.countByUserIdAndRevokedAtIsNull(userId);
    }

    // ── Mutation methods ──────────────────────────────────────────

    @Override
    @Transactional
    public Session save(Session session) {
        UserSessionEntity entity = toEntity(session);
        UserSessionEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public void delete(Session session) {
        UserSessionEntity entity = toEntity(session);
        jpaRepository.delete(entity);
    }

    // ── Domain ← Entity ──────────────────────────────────────────

    private Session toDomain(UserSessionEntity entity) {
        try {
            Constructor<Session> ctor = Session.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            Session session = ctor.newInstance();

            setField(session, "id", entity.getId());
            setField(session, "userId", entity.getUserId());
            setField(session, "sessionTokenHash", entity.getSessionTokenHash());
            setField(session, "clerkSessionId", entity.getClerkSessionId());
            setField(session, "deviceName", entity.getDeviceName());
            setField(session, "deviceType", entity.getDeviceType());
            setField(session, "browser", entity.getBrowser());
            setField(session, "os", entity.getOs());
            setField(session, "ipAddress", entity.getIpAddress());
            setField(session, "userAgent", entity.getUserAgent());
            setField(session, "deviceFingerprint", entity.getDeviceFingerprint());
            setField(session, "countryCode", entity.getCountryCode());
            setField(session, "city", entity.getCity());
            setField(session, "createdAt", entity.getCreatedAt());
            // No updated_at in entity — use lastActiveAt as a reasonable proxy
            setField(session, "updatedAt", entity.getLastActiveAt());
            setField(session, "lastActiveAt", entity.getLastActiveAt());
            setField(session, "expiresAt", entity.getExpiresAt());
            setField(session, "revokedAt", entity.getRevokedAt());
            setField(session, "revokeReason", entity.getRevokeReason());
            setField(session, "isTrusted", toBool(entity.getIsTrusted()));
            setField(session, "trustedUntil", entity.getTrustedUntil());
            setField(session, "isImpersonation", toBool(entity.getIsImpersonation()));
            setField(session, "impersonatedByAdminId", entity.getImpersonatedByAdminId());
            setField(session, "impersonationReason", entity.getImpersonationReason());

            return session;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reconstitute Session entity from JPA entity", e);
        }
    }

    // ── Entity ← Domain ──────────────────────────────────────────

    private UserSessionEntity toEntity(Session session) {
        // Resolve the UserEntity relationship via userId
        UserEntity userEntity = null;
        if (session.getUserId() != null) {
            userEntity = userJpaRepository.findById(session.getUserId()).orElse(null);
        }

        return UserSessionEntity.builder()
                .id(session.getId())
                .user(userEntity)
                .userId(session.getUserId())
                .sessionTokenHash(session.getSessionTokenHash())
                .clerkSessionId(session.getClerkSessionId())
                .deviceName(session.getDeviceName())
                .deviceType(session.getDeviceType())
                .browser(session.getBrowser())
                .os(session.getOs())
                .ipAddress(session.getIpAddress())
                .userAgent(session.getUserAgent())
                .deviceFingerprint(session.getDeviceFingerprint())
                .countryCode(session.getCountryCode())
                .city(session.getCity())
                .createdAt(session.getCreatedAt())
                .lastActiveAt(session.getLastActiveAt())
                .expiresAt(session.getExpiresAt())
                .revokedAt(session.getRevokedAt())
                .revokeReason(session.getRevokeReason())
                .isTrusted(session.isTrusted())
                .trustedUntil(session.getTrustedUntil())
                .isImpersonation(session.isImpersonation())
                .impersonatedByAdminId(session.getImpersonatedByAdminId())
                .impersonationReason(session.getImpersonationReason())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Walk up the class hierarchy to find a declared field. */
    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + clazz.getName() + " hierarchy");
    }

    private static boolean toBool(Boolean v) {
        return v != null && v;
    }
}
