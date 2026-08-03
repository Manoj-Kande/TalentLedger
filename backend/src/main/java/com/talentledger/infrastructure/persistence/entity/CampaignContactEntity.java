package com.talentledger.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping the {@code campaign_contacts} junction table.
 *
 * <p>Composite primary key: (campaign_id, contact_id). Uses {@link IdClass}.
 * Tracks per-contact outreach status, delivery timestamps, and metadata.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "campaign_contacts")
@IdClass(CampaignContactEntity.CampaignContactId.class)
public class CampaignContactEntity {

    @Id
    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Id
    @Column(name = "contact_id", updatable = false)
    private UUID contactId;

    /** PENDING, SENT, REPLIED, BOUNCED, UNSUBSCRIBED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CampaignContactStatus status = CampaignContactStatus.PENDING;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "email_subject", length = 500)
    private String emailSubject;

    @Column(name = "email_body", columnDefinition = "TEXT")
    private String emailBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "contact_deleted_at")
    private Instant contactDeletedAt;

    // ── Enum ──────────────────────────────────────────────

    /**
     * Contact status within a campaign, mirroring the
     * {@code campaign_contacts.status} CHECK constraint.
     */
    public enum CampaignContactStatus {
        PENDING,
        SENT,
        REPLIED,
        BOUNCED,
        UNSUBSCRIBED
    }

    // ── Composite Key Class ──────────────────────────────

    /**
     * Serializable composite primary key for the {@code campaign_contacts} table.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignContactId implements Serializable {

        private static final long serialVersionUID = 1L;

        private UUID campaignId;
        private UUID contactId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CampaignContactId that)) return false;
            return Objects.equals(campaignId, that.campaignId)
                    && Objects.equals(contactId, that.contactId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(campaignId, contactId);
        }
    }
}
