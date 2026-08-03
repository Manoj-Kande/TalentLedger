package com.talentledger.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "webhook_idempotency")
public class WebhookIdempotencyEntity {

    @Id
    @Column(name = "clerk_event_id", nullable = false, length = 255)
    private String clerkEventId;

    @Builder.Default
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    @Builder.Default
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt = Instant.now().plusSeconds(7 * 24 * 3600);
}
