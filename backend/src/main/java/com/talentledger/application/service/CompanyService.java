package com.talentledger.application.service;

import com.talentledger.application.dto.response.CompanyResponse;
import com.talentledger.application.dto.response.ContactResponse;
import com.talentledger.application.port.inbound.CompanyUseCase;
import com.talentledger.application.port.inbound.CompanyUseCase.CompanyContactsResult;
import com.talentledger.domain.shared.Result;
import com.talentledger.infrastructure.persistence.entity.CompanyEntity;
import com.talentledger.infrastructure.persistence.entity.ContactEntity;
import com.talentledger.infrastructure.persistence.repository.CompanyJpaRepository;
import com.talentledger.infrastructure.persistence.repository.ContactJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService implements CompanyUseCase {

    private final ContactJpaRepository contactJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;

    @Override
    public Result<List<CompanyResponse>, String> listCompanies(UUID userId) {
        List<UUID> companyIds = contactJpaRepository.findDistinctCompanyIdsByUserId(userId);
        if (companyIds.isEmpty()) {
            return Result.success(List.of());
        }
        List<CompanyEntity> companies = companyJpaRepository.findByIdIn(companyIds);
        List<CompanyResponse> responses = companies.stream()
                .map(c -> toResponse(c, (int) contactJpaRepository.countByCompanyIdAndUserIdAndDeletedAtIsNull(c.getId(), userId)))
                .toList();
        return Result.success(responses);
    }

    @Override
    public Result<CompanyResponse, String> getCompany(UUID companyId, UUID userId) {
        return companyJpaRepository.findById(companyId)
                .map(c -> Result.<CompanyResponse, String>success(toResponse(
                        c,
                        (int) contactJpaRepository.countByCompanyIdAndUserIdAndDeletedAtIsNull(companyId, userId))))
                .orElseGet(() -> Result.failure("Company not found"));
    }

    @Override
    public Result<List<CompanyResponse>, String> searchCompanies(UUID userId, String query) {
        List<CompanyEntity> companies = companyJpaRepository.searchCompanies(query, PageRequest.of(0, 50));
        List<CompanyResponse> responses = companies.stream()
                .map(c -> toResponse(c, (int) contactJpaRepository.countByCompanyIdAndUserIdAndDeletedAtIsNull(c.getId(), userId)))
                .toList();
        return Result.success(responses);
    }

    @Override
    public Result<CompanyContactsResult, String> getCompanyContacts(UUID companyId, UUID userId, String cursor, int size) {
        var pageable = PageRequest.of(0, size + 1);

        List<ContactEntity> entities;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = cursor.split("\\|");
                var cursorAt = java.time.Instant.parse(parts[0]);
                UUID cursorId = UUID.fromString(parts[1]);
                entities = contactJpaRepository.findByCompanyIdAndUserIdCursor(companyId, userId, cursorAt, cursorId, pageable);
            } catch (Exception e) {
                log.warn("Invalid company-contacts cursor, falling back to first page: {}", cursor);
                entities = contactJpaRepository.findByCompanyIdAndUserId(companyId, userId, pageable);
            }
        } else {
            entities = contactJpaRepository.findByCompanyIdAndUserId(companyId, userId, pageable);
        }

        boolean hasMore = entities.size() > size;
        List<ContactEntity> page = hasMore ? entities.subList(0, size) : entities;
        String nextCursor = (hasMore && !page.isEmpty())
                ? page.get(page.size() - 1).getCreatedAt() + "|" + page.get(page.size() - 1).getId()
                : null;

        List<ContactResponse> contacts = page.stream()
                .map(this::toContactResponse)
                .toList();

        return Result.success(new CompanyContactsResult(contacts, nextCursor, hasMore));
    }

    private CompanyResponse toResponse(CompanyEntity c, int contactCount) {
        return new CompanyResponse(
                c.getId(), c.getNormalizedName(), c.getDisplayName(),
                c.getCategory() != null ? c.getCategory().name() : null,
                c.getIndustry(), c.getSizeRange(), c.getHeadquarters(),
                c.getDomain(), c.getLogoUrl(), contactCount, c.getCreatedAt());
    }

    private ContactResponse toContactResponse(ContactEntity e) {
        return new ContactResponse(
                e.getId(), e.getName(),
                e.getEmail(), e.getNormalizedEmail(),
                e.getPhone(), e.getLinkedinUrl(), e.getSecondaryEmail(),
                e.getTitle(), e.getDepartment(),
                e.getSeniorityLevel() != null ? e.getSeniorityLevel().name() : null,
                e.getLocation(), e.getTimezone(), e.getLanguage(),
                e.getDomain(), e.getVerificationScore(), e.getSource(),
                e.getPrimaryDumpId(), e.getCompanyId(), null,
                e.getNotes(),
                e.getTags() != null ? new ArrayList<>(e.getTags()) : new ArrayList<>(),
                e.getCustomFields() != null ? new HashMap<>(e.getCustomFields()) : new HashMap<>(),
                e.getAiEnrichment() != null ? new HashMap<>(e.getAiEnrichment()) : new HashMap<>(),
                e.getStatus() != null ? e.getStatus().name() : null,
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
