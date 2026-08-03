package com.talentledger.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code user_quotas} table.
 *
 * <p>Shares the primary key with {@link UserEntity} via {@code @MapsId}.
 * All 14 columns.
 *
 * <p>Implements {@link Persistable} because {@code userId} is a manually
 * assigned (not {@code @GeneratedValue}) id. Without this, Spring Data's
 * default {@code isNew()} check sees a non-null id and calls {@code merge()}
 * instead of {@code persist()} for a brand-new row — which, combined with
 * the {@code @MapsId} one-to-one association, causes Hibernate to throw
 * {@code AssertionFailure: null identifier} on first insert (e.g. during
 * registration). {@code isNew} defaults to {@code true} for freshly built
 * instances and flips to {@code false} once the entity has actually been
 * loaded from or written to the database, so subsequent updates correctly
 * go through {@code merge()}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_quotas")
public class UserQuotaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "active_dumps_count", nullable = false)
    @Builder.Default
    private Integer activeDumpsCount = 0;

    @Column(name = "active_dumps_limit", nullable = false)
    @Builder.Default
    private Integer activeDumpsLimit = 1;

    @Column(name = "contacts_stored_count", nullable = false)
    @Builder.Default
    private Integer contactsStoredCount = 0;

    @Column(name = "contacts_stored_limit", nullable = false)
    @Builder.Default
    private Integer contactsStoredLimit = 0;

    @Column(name = "uploads_this_month_count", nullable = false)
    @Builder.Default
    private Integer uploadsThisMonthCount = 0;

    @Column(name = "uploads_monthly_limit", nullable = false)
    @Builder.Default
    private Integer uploadsMonthlyLimit = 5;

    @Column(name = "ai_credits_used", nullable = false)
    @Builder.Default
    private Integer aiCreditsUsed = 0;

    @Column(name = "ai_credits_limit", nullable = false)
    @Builder.Default
    private Integer aiCreditsLimit = 0;

    @Column(name = "storage_bytes_used", nullable = false)
    @Builder.Default
    private Long storageBytesUsed = 0L;

    @Column(name = "storage_bytes_limit", nullable = false)
    @Builder.Default
    private Long storageBytesLimit = 5242880L;

    @Column(name = "has_active_free_dump", nullable = false)
    @Builder.Default
    private Boolean hasActiveFreeDump = false;

    @Column(name = "last_reset_at")
    private Instant lastResetAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // ── Persistable<UUID> ─────────────────────────────────────────
    // Tracks whether this instance still needs an INSERT (persist) vs.
    // has already been persisted/loaded and should go through UPDATE (merge).

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public UUID getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}