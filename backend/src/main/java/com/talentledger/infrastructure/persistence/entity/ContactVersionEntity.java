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
 * JPA entity mapping the {@code contact_versions} audit-trail table.
 *
 * <p>All 9 columns. Tracks every mutation on a contact aggregate with
 * JSONB snapshots of field_changed, old_values, and new_values.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "contact_versions")
public class ContactVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "contact_id", nullable = false, updatable = false)
    private UUID contactId;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    /** CREATE, UPDATE, DELETE, RESTORE, BULK_UPDATE, AI_ENRICH, MERGE */
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_changed", columnDefinition = "jsonb")
    private Map<String, Object> fieldChanged;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private Map<String, Object> oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private Map<String, Object> newValues;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // ── Enum ──────────────────────────────────────────────

    /**
     * Change-type enum mirroring the {@code contact_versions.change_type} CHECK constraint.
     */
    public enum ChangeType {
        CREATE,
        UPDATE,
        DELETE,
        RESTORE,
        BULK_UPDATE,
        AI_ENRICH,
        MERGE
    }
}
