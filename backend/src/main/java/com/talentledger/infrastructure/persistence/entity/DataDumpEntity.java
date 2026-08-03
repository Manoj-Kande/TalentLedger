package com.talentledger.infrastructure.persistence.entity;

import com.talentledger.domain.dump.DumpStatus;
import com.talentledger.domain.dump.FileType;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity mapping the {@code data_dumps} table.
 *
 * <p>All 35 columns (id + 32 data + created_at + updated_at + completed_at + deleted_at),
 * with JSONB fields for tags, column_mapping, and parse_errors.
 *
 * <p>Database-level CHECK constraints on {@code status} and {@code (deleted_at IS NULL OR status IN ('EXPIRED','DELETED'))}
 * are enforced by the schema and not duplicated here.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "data_dumps")
public class DataDumpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> tags = List.of();

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    private FileType fileType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mapping", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> columnMapping = Map.of();

    @Column(name = "column_mapping_confidence", precision = 3, scale = 2)
    private BigDecimal columnMappingConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DumpStatus status = DumpStatus.PENDING;

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private Integer totalRows = 0;

    @Column(name = "parsed_contacts_count", nullable = false)
    @Builder.Default
    private Integer parsedContactsCount = 0;

    @Column(name = "live_contacts_count", nullable = false)
    @Builder.Default
    private Integer liveContactsCount = 0;

    @Column(name = "duplicate_within_dump_count", nullable = false)
    @Builder.Default
    private Integer duplicateWithinDumpCount = 0;

    @Column(name = "cross_dump_duplicate_count", nullable = false)
    @Builder.Default
    private Integer crossDumpDuplicateCount = 0;

    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private Integer errorCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parse_errors", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> parseErrors = List.of();

    @Column(name = "parse_duration_ms")
    private Integer parseDurationMs;

    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private Boolean isPinned = false;

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private Boolean isArchived = false;

    @Column(name = "is_persisted", nullable = false)
    @Builder.Default
    private Boolean isPersisted = false;

    @Column(name = "storage_provider", length = 20)
    @Builder.Default
    private String storageProvider = "R2";

    @Column(name = "original_file_key", length = 500)
    private String originalFileKey;

    @Column(name = "parsed_snapshot_key", length = 500)
    private String parsedSnapshotKey;

    @Column(name = "original_file_deleted_at")
    private Instant originalFileDeletedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
