package com.talentledger.infrastructure.persistence.entity;

import com.talentledger.domain.contact.ContactStatus;
import com.talentledger.domain.contact.SeniorityLevel;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity mapping the {@code contacts} table — the "Golden Table".
 *
 * <p>All 32 columns. JSONB fields for tags, custom_fields, and ai_enrichment.
 * Soft-delete via {@code deleted_at}; status must be {@code DELETED} when soft-deleted
 * (enforced by DB CHECK constraint).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "contacts")
public class ContactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "primary_dump_id")
    private UUID primaryDumpId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "normalized_email", length = 255)
    private String normalizedEmail;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    @Column(name = "secondary_email", length = 255)
    private String secondaryEmail;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "department", length = 100)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(name = "seniority_level", length = 20)
    private SeniorityLevel seniorityLevel;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "timezone", length = 50)
    private String timezone;

    @Column(name = "language", length = 50)
    private String language;

    @Column(name = "domain", length = 255)
    private String domain;

    @Column(name = "verification_score", nullable = false)
    @Builder.Default
    private Integer verificationScore = 0;

    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "source", length = 50)
    @Builder.Default
    private String source = "csv";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> tags = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> customFields = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_enrichment", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> aiEnrichment = Map.of();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ContactStatus status = ContactStatus.ACTIVE;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
