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
 * JPA entity mapping the {@code dump_contacts} junction table.
 *
 * <p>Links contacts to their source dumps with raw data, dedup flags,
 * and cross-dump match tracking. Soft-deleted via {@code deleted_at}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "dump_contacts")
public class DumpContactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dump_id", nullable = false, updatable = false)
    private DataDumpEntity dump;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false, updatable = false)
    private ContactEntity contact;

    @Column(name = "row_number")
    private Integer rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private Map<String, Object> rawData;

    @Column(name = "is_duplicate_within_dump", nullable = false)
    @Builder.Default
    private Boolean isDuplicateWithinDump = false;

    @Column(name = "is_cross_dump_duplicate", nullable = false)
    @Builder.Default
    private Boolean isCrossDumpDuplicate = false;

    @Column(name = "matched_existing_contact_id")
    private UUID matchedExistingContactId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
