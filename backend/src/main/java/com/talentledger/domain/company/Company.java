package com.talentledger.domain.company;

import com.talentledger.domain.shared.AggregateRoot;
import com.talentledger.domain.shared.BusinessRule;

import java.time.Instant;
import java.util.UUID;

/**
 * Company aggregate root.
 *
 * <p>Represents a company/organization in the system. Companies are shared
 * across all users — they are NOT user-scoped and have NO soft-delete.
 */
public class Company extends AggregateRoot<UUID> {

    private String normalizedName;
    private String displayName;
    private CompanyCategory category;
    private String industry;
    private String sizeRange;
    private String headquarters;
    private String domain;
    private String logoUrl;

    protected Company() {
        // for infrastructure (e.g. JPA/reflective) reconstitution
    }

    private Company(UUID id,
                    String normalizedName,
                    String displayName,
                    CompanyCategory category,
                    String industry,
                    String sizeRange,
                    String headquarters,
                    String domain,
                    String logoUrl) {
        this.id = id;
        this.normalizedName = normalizedName;
        this.displayName = displayName;
        this.category = category;
        this.industry = industry;
        this.sizeRange = sizeRange;
        this.headquarters = headquarters;
        this.domain = domain;
        this.logoUrl = logoUrl;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Factory method to create a new Company.
     *
     * @param displayName  the human-readable company name (required, max 255 chars)
     * @param category     optional business category
     * @param industry     optional industry descriptor
     * @param sizeRange    optional company size range (e.g. "50-200")
     * @param headquarters optional headquarters location
     * @param domain       optional web domain (e.g. "acme.com")
     * @param logoUrl      optional URL to company logo
     * @return a new Company instance
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException if displayName is invalid
     */
    public static Company create(String displayName,
                                 CompanyCategory category,
                                 String industry,
                                 String sizeRange,
                                 String headquarters,
                                 String domain,
                                 String logoUrl) {
        BusinessRule.ensure(displayName != null && !displayName.isBlank(),
                "Company display name must not be blank");
        BusinessRule.ensure(displayName.length() <= 255,
                "Company display name must not exceed 255 characters");

        String normalized = CompanyNormalizer.normalize(displayName);
        BusinessRule.ensure(!normalized.isBlank(),
                "Company normalized name must not be blank");
        BusinessRule.ensure(normalized.length() <= 255,
                "Company normalized name must not exceed 255 characters");

        return new Company(
                UUID.randomUUID(),
                normalized,
                displayName.trim(),
                category,
                industry,
                sizeRange,
                headquarters,
                domain,
                logoUrl
        );
    }

    // ── Getters ────────────────────────────────────────────

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CompanyCategory getCategory() {
        return category;
    }

    public String getIndustry() {
        return industry;
    }

    public String getSizeRange() {
        return sizeRange;
    }

    public String getHeadquarters() {
        return headquarters;
    }

    public String getDomain() {
        return domain;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    // ── Setters (domain-validated) ─────────────────────────

    public void setDisplayName(String displayName) {
        BusinessRule.ensure(displayName != null && !displayName.isBlank(),
                "Company display name must not be blank");
        BusinessRule.ensure(displayName.length() <= 255,
                "Company display name must not exceed 255 characters");
        this.displayName = displayName.trim();
        this.normalizedName = CompanyNormalizer.normalize(displayName);
        BusinessRule.ensure(!this.normalizedName.isBlank(),
                "Company normalized name must not be blank");
        BusinessRule.ensure(this.normalizedName.length() <= 255,
                "Company normalized name must not exceed 255 characters");
        this.updatedAt = Instant.now();
    }

    public void setCategory(CompanyCategory category) {
        this.category = category;
        this.updatedAt = Instant.now();
    }

    public void setIndustry(String industry) {
        this.industry = industry;
        this.updatedAt = Instant.now();
    }

    public void setSizeRange(String sizeRange) {
        this.sizeRange = sizeRange;
        this.updatedAt = Instant.now();
    }

    public void setHeadquarters(String headquarters) {
        this.headquarters = headquarters;
        this.updatedAt = Instant.now();
    }

    public void setDomain(String domain) {
        this.domain = domain;
        this.updatedAt = Instant.now();
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
        this.updatedAt = Instant.now();
    }
}
