package com.talentledger.infrastructure.persistence.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity mapping the {@code ai_enrichments} table.
 *
 * <p>All 13 columns. Captures AI-generated enrichment results for contacts
 * with token usage tracking and confidence scoring.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ai_enrichments")
public class AiEnrichmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "contact_id", nullable = false, updatable = false)
    private UUID contactId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "prompt_id")
    private UUID promptId;

    /** EMAIL_DRAFT, HIRING_SIGNAL, PROFILE_SUMMARY, BEST_ANGLE, SENTIMENT */
    @Enumerated(EnumType.STRING)
    @Column(name = "enrichment_type", length = 20)
    private EnrichmentType enrichmentType;

    @Column(name = "model_used", length = 50)
    private String modelUsed;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "generated_content", columnDefinition = "TEXT")
    private String generatedContent;

    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // ── Enum ──────────────────────────────────────────────

    /**
     * Enrichment type enum mirroring the {@code ai_enrichments.enrichment_type} CHECK constraint.
     */
    public enum EnrichmentType {
        EMAIL_DRAFT,
        HIRING_SIGNAL,
        PROFILE_SUMMARY,
        BEST_ANGLE,
        SENTIMENT
    }
}
