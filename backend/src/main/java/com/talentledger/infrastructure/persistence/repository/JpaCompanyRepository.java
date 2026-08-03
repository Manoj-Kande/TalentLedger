package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.CompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CompanyEntity}.
 *
 * <p>Companies are shared (not user-scoped) with no soft-delete.
 */
public interface JpaCompanyRepository extends JpaRepository<CompanyEntity, UUID> {

    /**
     * Find a company by its normalized name.
     */
    Optional<CompanyEntity> findByNormalizedName(String normalizedName);

    /**
     * Find a company by its domain.
     */
    Optional<CompanyEntity> findByDomain(String domain);
}
