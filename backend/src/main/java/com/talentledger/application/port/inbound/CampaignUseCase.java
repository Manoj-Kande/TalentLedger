package com.talentledger.application.port.inbound;

import com.talentledger.application.dto.response.CampaignResponse;
import com.talentledger.application.dto.response.ContactResponse;
import com.talentledger.domain.shared.Result;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port — Campaign use cases.
 * Implemented by CampaignService in the application layer.
 */
public interface CampaignUseCase {

    /** List all campaigns for the user. */
    Result<List<CampaignResponse>, String> listCampaigns(UUID userId);

    /** Get a single campaign by ID. */
    Result<CampaignResponse, String> getCampaign(UUID campaignId, UUID userId);

    /** Create a new campaign (starts in DRAFT status). */
    Result<CampaignResponse, String> createCampaign(UUID userId, CreateCampaignCommand command);

    /** Update campaign metadata (name, description, schedule). */
    Result<CampaignResponse, String> updateCampaign(UUID campaignId, UUID userId, UpdateCampaignCommand command);

    /** Delete a campaign. */
    Result<Void, String> deleteCampaign(UUID campaignId, UUID userId);

    /** Transition campaign status (activate, pause, resume, complete, archive). */
    Result<CampaignResponse, String> transitionStatus(UUID campaignId, UUID userId, String action);

    /** Add contacts to a campaign. */
    Result<Void, String> addContacts(UUID campaignId, UUID userId, List<UUID> contactIds);

    /** Remove a contact from a campaign. */
    Result<Void, String> removeContact(UUID campaignId, UUID userId, UUID contactId);

    /** Get contacts in a campaign (cursor paginated). */
    Result<CampaignContactsResult, String> getCampaignContacts(UUID campaignId, UUID userId, String cursor, int size);

    // ── Inner types ──────────────────────────────────────

    record CreateCampaignCommand(String name, String description) {}
    record UpdateCampaignCommand(String name, String description, String scheduledAt) {}

    record CampaignContactsResult(List<ContactResponse> contacts, String nextCursor, boolean hasMore) {}
}
