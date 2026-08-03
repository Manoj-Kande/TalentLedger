package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.domain.company.CompanyCategory;
import com.talentledger.infrastructure.persistence.entity.CompanyEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CompanyEntity}.
 */
public interface CompanyJpaRepository extends JpaRepository<CompanyEntity, UUID> {

    Optional<CompanyEntity> findByNormalizedName(String normalizedName);

    Optional<CompanyEntity> findByDomain(String domain);

    List<CompanyEntity> findByCategory(CompanyCategory category);

    List<CompanyEntity> findByIdIn(List<UUID> ids);

    List<CompanyEntity> findAllByOrderByDisplayNameAsc();

    @Query("SELECT c FROM CompanyEntity c WHERE LOWER(c.displayName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.domain) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<CompanyEntity> searchCompanies(@Param("query") String query, Pageable pageable);
}
