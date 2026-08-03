package com.talentledger.infrastructure.persistence.entity;

import com.talentledger.domain.company.CompanyCategory;
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

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code companies} table.
 *
 * <p>All 11 columns. Companies are shared (not user-scoped) with no soft-delete.
 * {@code normalizedName} carries a unique constraint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "companies")
public class CompanyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "normalized_name", nullable = false, unique = true, length = 255)
    private String normalizedName;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private CompanyCategory category;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "size_range", length = 50)
    private String sizeRange;

    @Column(name = "headquarters", length = 100)
    private String headquarters;

    @Column(name = "domain", length = 255)
    private String domain;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
