package com.talentledger.infrastructure.persistence.entity;

import com.talentledger.domain.user.UserPlan;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity mapping the {@code subscription_events} table.
 *
 * <p>All 9 columns. JSONB metadata field.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "subscription_events")
public class SubscriptionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false, updatable = false)
    private SubscriptionEntity subscription;

    @Column(name = "subscription_id", nullable = false, updatable = false, insertable = false)
    private UUID subscriptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserEntity user;

    @Column(name = "user_id", nullable = false, updatable = false, insertable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private SubscriptionEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_plan", length = 20)
    private UserPlan fromPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_plan", length = 20)
    private UserPlan toPlan;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /** Stripe (or other provider) event id, for webhook-redelivery idempotency. See V2 migration. */
    @Column(name = "provider_event_id", length = 255)
    private String providerEventId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
