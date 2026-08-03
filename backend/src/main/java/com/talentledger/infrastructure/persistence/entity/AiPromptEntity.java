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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity mapping the {@code ai_prompts} table.
 *
 * <p>All 14 columns. Stores reusable prompt templates with optional
 * variable definitions and model configuration as JSONB.
 * Soft-deleted via {@code deleted_at}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ai_prompts")
public class AiPromptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** COLD_EMAIL, DATA_EXTRACTION, PROFILE_SUMMARY, HIRING_SIGNAL, CUSTOM */
    @Enumerated(EnumType.STRING)
    @Column(name = "prompt_type", length = 20)
    private PromptType promptType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables_json", columnDefinition = "jsonb")
    private Map<String, Object> variablesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_config", columnDefinition = "jsonb")
    private Map<String, Object> modelConfig;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Integer usageCount = 0;

    @Column(name = "avg_tokens_per_call")
    private Integer avgTokensPerCall;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ── Enum ──────────────────────────────────────────────

    /**
     * Prompt type enum mirroring the {@code ai_prompts.prompt_type} CHECK constraint.
     */
    public enum PromptType {
        COLD_EMAIL,
        DATA_EXTRACTION,
        PROFILE_SUMMARY,
        HIRING_SIGNAL,
        CUSTOM
    }
}
