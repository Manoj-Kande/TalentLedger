package com.talentledger.application.port.inbound;

import com.talentledger.application.dto.response.CompanyResponse;
import com.talentledger.application.dto.response.ContactResponse;
import com.talentledger.domain.shared.Result;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port — Company use cases.
 * Implemented by CompanyService in the application layer.
 */
public interface CompanyUseCase {

    /** Get all companies that have contacts for this user. */
    Result<List<CompanyResponse>, String> listCompanies(UUID userId);

    /** Get a single company by ID. */
    Result<CompanyResponse, String> getCompany(UUID companyId, UUID userId);

    /** Search companies by name or domain. */
    Result<List<CompanyResponse>, String> searchCompanies(UUID userId, String query);

    /** Get contacts belonging to a specific company for this user (cursor paginated). */
    Result<CompanyContactsResult, String> getCompanyContacts(UUID companyId, UUID userId, String cursor, int size);

    record CompanyContactsResult(List<ContactResponse> contacts, String nextCursor, boolean hasMore) {}
}
