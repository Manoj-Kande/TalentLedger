package com.talentledger.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code user_devices} table.
 *
 * <p>All 20 columns. UNIQUE(user_id, device_fingerprint) enforced
 * via {@link Table#uniqueConstraints}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_devices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "device_fingerprint"}))
public class UserDeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserEntity user;

    @Column(name = "user_id", nullable = false, updatable = false, insertable = false)
    private UUID userId;

    @Column(name = "device_fingerprint", nullable = false, length = 64)
    private String deviceFingerprint;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "browser", length = 100)
    private String browser;

    @Column(name = "os", length = 100)
    private String os;

    @Column(name = "is_trusted", nullable = false)
    @Builder.Default
    private Boolean isTrusted = false;

    @Column(name = "trusted_at")
    private Instant trustedAt;

    @Column(name = "trust_expires_at")
    private Instant trustExpiresAt;

    @Column(name = "is_2fa_trusted", nullable = false)
    @Builder.Default
    private Boolean is2faTrusted = false;

    @Column(name = "mfa_trusted_at")
    private Instant mfaTrustedAt;

    @Column(name = "mfa_trust_expires_at")
    private Instant mfaTrustExpiresAt;

    @Column(name = "first_seen_at", updatable = false)
    private Instant firstSeenAt;

    @Column(name = "first_seen_ip", length = 45)
    private String firstSeenIp;

    @Column(name = "first_seen_country", length = 2)
    private String firstSeenCountry;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_seen_ip", length = 45)
    private String lastSeenIp;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 50)
    private String revokeReason;
}
