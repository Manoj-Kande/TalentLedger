package com.talentledger.infrastructure.persistence.entity;

import com.talentledger.domain.user.UserPlan;
import com.talentledger.domain.user.UserRole;
import com.talentledger.domain.user.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity mapping the {@code users} table.
 *
 * <p>All 32 columns from the schema. JSONB fields ({@code onboarding_profile},
 * {@code email_notifications}) use {@code Map<String,Object>} with
 * {@link org.hibernate.annotations.JdbcTypeCode}.
 *
 * <p>Database-level CHECK constraints on {@code status/sync} are mirrored
 * via {@link Check} annotations.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
@Check(constraints = "(deleted_at IS NULL) OR (status IN ('PENDING_DELETION','DELETED'))")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "clerk_id", unique = true, length = 255)
    private String clerkId;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "is_guest", nullable = false)
    @Builder.Default
    private Boolean isGuest = false;

    @Column(name = "guest_expires_at")
    private Instant guestExpiresAt;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    @Builder.Default
    private UserPlan plan = UserPlan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "onboarding_completed", nullable = false)
    @Builder.Default
    private Boolean onboardingCompleted = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "onboarding_profile", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> onboardingProfile = Map.of();

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "account_locked_until")
    private Instant accountLockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "last_login_user_agent", columnDefinition = "TEXT")
    private String lastLoginUserAgent;

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private Boolean mfaEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "mfa_type", length = 20)
    private MfaType mfaType;

    @Column(name = "mfa_secret_encrypted", columnDefinition = "TEXT")
    private String mfaSecretEncrypted;

    @Column(name = "mfa_setup_completed_at")
    private Instant mfaSetupCompletedAt;

    @Column(name = "mfa_backup_codes_remaining", nullable = false)
    @Builder.Default
    private Integer mfaBackupCodesRemaining = 0;

    @Column(name = "timezone", nullable = false, length = 50)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "locale", nullable = false, length = 10)
    @Builder.Default
    private String locale = "en-US";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "email_notifications", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> emailNotifications = Map.of();

    @Column(name = "accepted_terms_at")
    private Instant acceptedTermsAt;

    @Column(name = "accepted_privacy_at")
    private Instant acceptedPrivacyAt;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "data_export_requested_at")
    private Instant dataExportRequestedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
