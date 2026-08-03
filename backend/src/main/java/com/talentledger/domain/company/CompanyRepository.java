package com.talentledger.domain.company;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and retrieving {@link Company} aggregates.
 *
 * <p>Implemented by the infrastructure adapter (e.g. JPA repository).
 */
public interface CompanyRepository {

    Optional<Company> findByNormalizedName(String normalizedName);

    Optional<Company> findById(UUID id);

    Company save(Company company);

    Optional<Company> findByDomain(String domain);

    List<Company> findByCategory(CompanyCategory category);
}
