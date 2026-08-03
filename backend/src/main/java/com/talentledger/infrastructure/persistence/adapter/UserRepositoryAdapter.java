package com.talentledger.infrastructure.persistence.adapter;

import com.talentledger.domain.user.User;
import com.talentledger.domain.user.UserRepository;
import com.talentledger.infrastructure.persistence.entity.MfaType;
import com.talentledger.infrastructure.persistence.entity.UserEntity;
import com.talentledger.infrastructure.persistence.repository.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that bridges the {@link UserRepository} domain port
 * to the Spring Data JPA {@link com.talentledger.infrastructure.persistence.repository.JpaUserRepository}.
 *
 * <p>Uses reflection to reconstitute the immutable {@link User} aggregate
 * from JPA entities, since the domain class exposes only package-private
 * setters (inaccessible from this package) and a private constructor.
 */
@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpaRepository;

    // ── Query methods ────────────────────────────────────────────

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<User> findByClerkId(String clerkId) {
        return jpaRepository.findByClerkId(clerkId).map(this::toDomain);
    }

    // ── Mutation methods ──────────────────────────────────────────

    @Override
    public User save(User user) {
        UserEntity entity = toEntity(user);
        UserEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    // ── Existence checks ─────────────────────────────────────────

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByEmailExcludingDeleted(String email) {
        return jpaRepository.existsByEmailAndDeletedAtIsNull(email);
    }

    @Override
    public List<User> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    // ── Domain ← Entity ──────────────────────────────────────────

    private User toDomain(UserEntity entity) {
        try {
            Constructor<User> ctor = User.class.getDeclaredConstructor(UUID.class);
            ctor.setAccessible(true);
            User user = ctor.newInstance(entity.getId());

            setField(user, "email", entity.getEmail());
            setField(user, "clerkId", entity.getClerkId());
            setField(user, "emailVerified", toBool(entity.getEmailVerified()));
            setField(user, "name", entity.getName());
            setField(user, "avatarUrl", entity.getAvatarUrl());
            setField(user, "role", entity.getRole());
            setField(user, "plan", entity.getPlan());
            setField(user, "status", entity.getStatus());
            setField(user, "passwordHash", entity.getPasswordHash());
            setField(user, "onboardingCompleted", toBool(entity.getOnboardingCompleted()));
            setField(user, "onboardingProfile",
                    entity.getOnboardingProfile() != null ? new HashMap<>(entity.getOnboardingProfile()) : new HashMap<>());
            setField(user, "failedLoginAttempts", entity.getFailedLoginAttempts());
            setField(user, "accountLockedUntil", entity.getAccountLockedUntil());
            setField(user, "lastLoginAt", entity.getLastLoginAt());
            setField(user, "lastLoginIp", entity.getLastLoginIp());
            setField(user, "lastLoginUserAgent", entity.getLastLoginUserAgent());
            setField(user, "mfaEnabled", toBool(entity.getMfaEnabled()));
            setField(user, "mfaType",
                    entity.getMfaType() != null ? entity.getMfaType().name() : null);
            setField(user, "mfaSecretEncrypted", entity.getMfaSecretEncrypted());
            setField(user, "mfaSetupCompletedAt", entity.getMfaSetupCompletedAt());
            setField(user, "mfaBackupCodesRemaining", entity.getMfaBackupCodesRemaining());
            setField(user, "timezone", entity.getTimezone());
            setField(user, "locale", entity.getLocale());

            // email_notifications: Map<String, Object> → Map<String, Boolean>
            Map<String, Boolean> notifMap = new HashMap<>();
            if (entity.getEmailNotifications() != null) {
                for (Map.Entry<String, Object> entry : entity.getEmailNotifications().entrySet()) {
                    if (entry.getValue() instanceof Boolean) {
                        notifMap.put(entry.getKey(), (Boolean) entry.getValue());
                    }
                }
            }
            setField(user, "emailNotifications", notifMap);

            setField(user, "acceptedTermsAt", entity.getAcceptedTermsAt());
            setField(user, "acceptedPrivacyAt", entity.getAcceptedPrivacyAt());
            setField(user, "deletionRequestedAt", entity.getDeletionRequestedAt());
            setField(user, "dataExportRequestedAt", entity.getDataExportRequestedAt());

            // AggregateRoot fields
            setField(user, "createdAt", entity.getCreatedAt());
            setField(user, "updatedAt", entity.getUpdatedAt());
            setField(user, "deletedAt", entity.getDeletedAt());

            return user;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reconstitute User aggregate from entity", e);
        }
    }

    // ── Entity ← Domain ──────────────────────────────────────────

    private UserEntity toEntity(User user) {
        return UserEntity.builder()
                .id(user.getId())
                .clerkId(user.getClerkId())
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .plan(user.getPlan())
                .status(user.getStatus())
                .passwordHash(user.getPasswordHash())
                .onboardingCompleted(user.isOnboardingCompleted())
                .onboardingProfile(user.getOnboardingProfile())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .accountLockedUntil(user.getAccountLockedUntil())
                .lastLoginAt(user.getLastLoginAt())
                .lastLoginIp(user.getLastLoginIp())
                .lastLoginUserAgent(user.getLastLoginUserAgent())
                .mfaEnabled(user.isMfaEnabled())
                .mfaType(user.getMfaType() != null ? MfaType.valueOf(user.getMfaType()) : null)
                .mfaSecretEncrypted(user.getMfaSecretEncrypted())
                .mfaSetupCompletedAt(user.getMfaSetupCompletedAt())
                .mfaBackupCodesRemaining(user.getMfaBackupCodesRemaining())
                .timezone(user.getTimezone())
                .locale(user.getLocale())
                .emailNotifications(safeCopyBooleanMap(user.getEmailNotifications()))
                .acceptedTermsAt(user.getAcceptedTermsAt())
                .acceptedPrivacyAt(user.getAcceptedPrivacyAt())
                .deletionRequestedAt(user.getDeletionRequestedAt())
                .dataExportRequestedAt(user.getDataExportRequestedAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .deletedAt(user.getDeletedAt())
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> safeCopyBooleanMap(Map<String, Boolean> source) {
        if (source == null) return Map.of();
        Map<String, Object> target = new HashMap<>();
        for (Map.Entry<String, Boolean> entry : source.entrySet()) {
            target.put(entry.getKey(), entry.getValue());
        }
        return target;
    }
}
