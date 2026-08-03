package com.talentledger.infrastructure.persistence.adapter;

import com.talentledger.domain.company.Company;
import com.talentledger.domain.company.CompanyCategory;
import com.talentledger.domain.company.CompanyRepository;
import com.talentledger.infrastructure.persistence.entity.CompanyEntity;
import com.talentledger.infrastructure.persistence.repository.CompanyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that bridges the {@link CompanyRepository} domain port
 * to the Spring Data JPA {@link CompanyJpaRepository}.
 *
 * <p>{@link Company} has a protected no-arg constructor for infrastructure
 * reconstitution, but it resides in a different package, so reflection is used.
 */
@Component
@RequiredArgsConstructor
public class CompanyRepositoryAdapter implements CompanyRepository {

    private final CompanyJpaRepository jpaRepository;

    // ── Query methods ────────────────────────────────────────────

    @Override
    public Optional<Company> findByNormalizedName(String normalizedName) {
        return jpaRepository.findByNormalizedName(normalizedName).map(this::toDomain);
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Company> findByDomain(String domain) {
        return jpaRepository.findByDomain(domain).map(this::toDomain);
    }

    @Override
    public List<Company> findByCategory(CompanyCategory category) {
        return jpaRepository.findByCategory(category)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // ── Mutation methods ──────────────────────────────────────────

    @Override
    @Transactional
    public Company save(Company company) {
        CompanyEntity entity = toEntity(company);
        CompanyEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    // ── Domain ← Entity ──────────────────────────────────────────

    private Company toDomain(CompanyEntity entity) {
        try {
            Constructor<Company> ctor = Company.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            Company company = ctor.newInstance();

            setField(company, "id", entity.getId());
            setField(company, "normalizedName", entity.getNormalizedName());
            setField(company, "displayName", entity.getDisplayName());
            setField(company, "category", entity.getCategory());
            setField(company, "industry", entity.getIndustry());
            setField(company, "sizeRange", entity.getSizeRange());
            setField(company, "headquarters", entity.getHeadquarters());
            setField(company, "domain", entity.getDomain());
            setField(company, "logoUrl", entity.getLogoUrl());

            // AggregateRoot fields
            setField(company, "createdAt", entity.getCreatedAt());
            setField(company, "updatedAt", entity.getUpdatedAt());

            return company;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reconstitute Company aggregate from entity", e);
        }
    }

    // ── Entity ← Domain ──────────────────────────────────────────

    private CompanyEntity toEntity(Company company) {
        return CompanyEntity.builder()
                .id(company.getId())
                .normalizedName(company.getNormalizedName())
                .displayName(company.getDisplayName())
                .category(company.getCategory())
                .industry(company.getIndustry())
                .sizeRange(company.getSizeRange())
                .headquarters(company.getHeadquarters())
                .domain(company.getDomain())
                .logoUrl(company.getLogoUrl())
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Walk up the class hierarchy to find a declared field. */
    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + clazz.getName() + " hierarchy");
    }
}
