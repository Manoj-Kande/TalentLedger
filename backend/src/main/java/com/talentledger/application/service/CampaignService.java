package com.talentledger.application.service;

import com.talentledger.application.dto.response.CampaignResponse;
import com.talentledger.application.dto.response.ContactResponse;
import com.talentledger.application.port.inbound.CampaignUseCase;
import com.talentledger.application.port.inbound.CampaignUseCase.CampaignContactsResult;
import com.talentledger.application.port.inbound.CampaignUseCase.CreateCampaignCommand;
import com.talentledger.application.port.inbound.CampaignUseCase.UpdateCampaignCommand;
import com.talentledger.domain.outreach.Campaign;
import com.talentledger.domain.outreach.CampaignRepository;
import com.talentledger.domain.shared.Result;
import com.talentledger.infrastructure.persistence.entity.CampaignContactEntity;
import com.talentledger.infrastructure.persistence.entity.ContactEntity;
import com.talentledger.infrastructure.persistence.repository.ContactJpaRepository;
import com.talentledger.infrastructure.persistence.repository.JpaCampaignContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService implements CampaignUseCase {

    private final CampaignRepository campaignRepository;
    private final JpaCampaignContactRepository campaignContactRepository;
    private final ContactJpaRepository contactJpaRepository;

    @Override
    public Result<List<CampaignResponse>, String> listCampaigns(UUID userId) {
        List<Campaign> campaigns = campaignRepository.findByUserId(userId);
        List<CampaignResponse> responses = campaigns.stream().map(this::toResponse).toList();
        return Result.success(responses);
    }

    @Override
    public Result<CampaignResponse, String> getCampaign(UUID campaignId, UUID userId) {
        return campaignRepository.findByIdAndUserId(campaignId, userId)
                .map(c -> Result.<CampaignResponse, String>success(toResponse(c)))
                .orElseGet(() -> Result.failure("Campaign not found"));
    }

    @Override
    @Transactional
    public Result<CampaignResponse, String> createCampaign(UUID userId, CreateCampaignCommand command) {
        Campaign campaign = Campaign.create(userId, command.name());
        if (command.description() != null) {
            campaign.setDescription(command.description());
        }
        Campaign saved = campaignRepository.save(campaign);
        return Result.success(toResponse(saved));
    }

    @Override
    @Transactional
    public Result<CampaignResponse, String> updateCampaign(UUID campaignId, UUID userId, UpdateCampaignCommand command) {
        return campaignRepository.findByIdAndUserId(campaignId, userId)
                .map(c -> {
                    if (command.name() != null) c.rename(command.name());
                    if (command.description() != null) c.setDescription(command.description());
                    if (command.scheduledAt() != null) {
                        try {
                            c.setScheduledAt(Instant.parse(command.scheduledAt()));
                        } catch (Exception e) {
                            log.warn("Invalid scheduledAt format: {}", command.scheduledAt());
                        }
                    }
                    Campaign saved = campaignRepository.save(c);
                    return Result.<CampaignResponse, String>success(toResponse(saved));
                })
                .orElseGet(() -> Result.failure("Campaign not found"));
    }

    @Override
    @Transactional
    public Result<Void, String> deleteCampaign(UUID campaignId, UUID userId) {
        return campaignRepository.findByIdAndUserId(campaignId, userId)
                .<Result<Void, String>>map(c -> {
                    // Delete all junction records first
                    campaignContactRepository.deleteAllByCampaignId(campaignId);
                    campaignRepository.delete(c);
                    log.info("Campaign {} deleted by user {}", campaignId, userId);
                    return Result.success(null);
                })
                .orElseGet(() -> Result.failure("Campaign not found"));
    }

    @Override
    @Transactional
    public Result<CampaignResponse, String> transitionStatus(UUID campaignId, UUID userId, String action) {
        return campaignRepository.findByIdAndUserId(campaignId, userId)
                .map(c -> {
                    switch (action.toUpperCase()) {
                        case "ACTIVATE" -> c.activate();
                        case "PAUSE" -> c.pause();
                        case "RESUME" -> c.resume();
                        case "COMPLETE" -> c.complete();
                        case "ARCHIVE" -> c.archive();
                        default -> throw new IllegalArgumentException("Invalid action: " + action);
                    }
                    Campaign saved = campaignRepository.save(c);
                    return Result.<CampaignResponse, String>success(toResponse(saved));
                })
                .orElseGet(() -> Result.failure("Campaign not found"));
    }

    @Override
    @Transactional
    public Result<Void, String> addContacts(UUID campaignId, UUID userId, List<UUID> contactIds) {
        return campaignRepository.findByIdAndUserId(campaignId, userId)
                .<Result<Void, String>>map(c -> {
                    int added = 0;
                    for (UUID contactId : contactIds) {
                        // Verify contact belongs to user
                        if (!contactJpaRepository.existsByIdAndUserId(contactId, userId)) {
                            log.warn("Contact {} does not belong to user {}, skipping", contactId, userId);
                            continue;
                        }
                        // Check if already in campaign
                        if (campaignContactRepository.existsByCampaignIdAndContactId(campaignId, contactId)) {
                            log.debug("Contact {} already in campaign {}, skipping", contactId, campaignId);
                            continue;
                        }
                        CampaignContactEntity junction = CampaignContactEntity.builder()
                                .campaignId(campaignId)
                                .contactId(contactId)
                                .status(CampaignContactEntity.CampaignContactStatus.PENDING)
                                .build();
                        campaignContactRepository.save(junction);
                        added++;
                    }
                    c.setTotalContacts((int) campaignContactRepository.countByCampaignId(campaignId));
                    campaignRepository.save(c);
                    log.info("Added {} contacts to campaign {} by user {}", added, campaignId, userId);
                    return Result.success(null);
                })
                .orElseGet(() -> Result.failure("Campaign not found"));
    }

    @Override
    @Transactional
    public Result<Void, String> removeContact(UUID campaignId, UUID userId, UUID contactId) {
        return campaignRepository.findByIdAndUserId(campaignId, userId)
                .<Result<Void, String>>map(c -> {
                    campaignContactRepository.deleteByCampaignIdAndContactId(campaignId, contactId);
                    long count = campaignContactRepository.countByCampaignId(campaignId);
                    c.setTotalContacts((int) count);
                    campaignRepository.save(c);
                    log.info("Removed contact {} from campaign {} by user {}", contactId, campaignId, userId);
                    return Result.success(null);
                })
                .orElseGet(() -> Result.failure("Campaign not found"));
    }

    @Override
    public Result<CampaignContactsResult, String> getCampaignContacts(UUID campaignId, UUID userId, String cursor, int size) {
        if (!campaignRepository.findByIdAndUserId(campaignId, userId).isPresent()) {
            return Result.failure("Campaign not found");
        }

        var pageable = PageRequest.of(0, size + 1);
        List<CampaignContactEntity> junctions;
        if (cursor != null && !cursor.isBlank()) {
            try {
                UUID cursorContactId = UUID.fromString(cursor);
                junctions = campaignContactRepository.findByCampaignIdCursor(campaignId, cursorContactId, pageable);
            } catch (Exception e) {
                log.warn("Invalid campaign-contacts cursor, falling back to first page: {}", cursor);
                junctions = campaignContactRepository.findByCampaignId(campaignId, pageable).getContent();
            }
        } else {
            junctions = campaignContactRepository.findByCampaignId(campaignId, pageable).getContent();
        }

        boolean hasMore = junctions.size() > size;
        List<CampaignContactEntity> page = hasMore ? junctions.subList(0, size) : junctions;

        List<ContactResponse> contacts = new ArrayList<>();
        for (CampaignContactEntity jc : page) {
            contactJpaRepository.findByIdAndUserId(jc.getContactId(), userId)
                    .ifPresent(entity -> contacts.add(entityToResponse(entity)));
        }

        String nextCursor = hasMore && !page.isEmpty()
                ? page.get(page.size() - 1).getContactId().toString()
                : null;

        return Result.success(new CampaignContactsResult(contacts, nextCursor, hasMore));
    }

    private CampaignResponse toResponse(Campaign c) {
        return new CampaignResponse(
                c.getId(), c.getName(), c.getDescription(), c.getTemplateId(),
                c.getStatus().name(), c.getTotalContacts(),
                c.getSentCount(), c.getReplyCount(), c.getBounceCount(),
                c.getScheduledAt(), c.getCompletedAt(),
                c.getCreatedAt(), c.getUpdatedAt());
    }

    private ContactResponse entityToResponse(ContactEntity e) {
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
