package com.talentledger.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code email_change_history} table.
 *
 * <p>All 10 columns.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "email_change_history")
public class EmailChangeHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserEntity user;

    @Column(name = "user_id", nullable = false, updatable = false, insertable = false)
    private UUID userId;

    @Column(name = "old_email", nullable = false, length = 255)
    private String oldEmail;

    @Column(name = "new_email", nullable = false, length = 255)
    private String newEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by", nullable = false, length = 50)
    private EmailChangeReason changedBy;

    @Column(name = "changed_by_admin_id")
    private UUID changedByAdminId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
