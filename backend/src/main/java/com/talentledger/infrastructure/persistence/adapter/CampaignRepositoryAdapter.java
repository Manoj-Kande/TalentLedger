package com.talentledger.infrastructure.persistence.adapter;

import com.talentledger.domain.outreach.Campaign;
import com.talentledger.domain.outreach.CampaignRepository;
import com.talentledger.infrastructure.persistence.entity.CampaignEntity;
import com.talentledger.infrastructure.persistence.repository.CampaignJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that bridges the {@link CampaignRepository} domain port
 * to the Spring Data JPA {@link CampaignJpaRepository}.
 *
 * <p>{@link Campaign} has a protected no-arg constructor for infrastructure
 * reconstitution, but it resides in a different package, so reflection is used.
 */
@Component
@RequiredArgsConstructor
public class CampaignRepositoryAdapter implements CampaignRepository {

    private final CampaignJpaRepository jpaRepository;

    // ── Query methods ────────────────────────────────────────────

    @Override
    public Optional<Campaign> findByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    public List<Campaign> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // ── Mutation methods ──────────────────────────────────────────

    @Override
    @Transactional
    public Campaign save(Campaign campaign) {
        CampaignEntity entity = toEntity(campaign);
        CampaignEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public void delete(Campaign campaign) {
        CampaignEntity entity = toEntity(campaign);
        jpaRepository.delete(entity);
    }

    // ── Domain ← Entity ──────────────────────────────────────────

    private Campaign toDomain(CampaignEntity entity) {
        try {
            Constructor<Campaign> ctor = Campaign.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            Campaign campaign = ctor.newInstance();

            setField(campaign, "id", entity.getId());
            setField(campaign, "userId", entity.getUserId());
            setField(campaign, "name", entity.getName());
            setField(campaign, "description", entity.getDescription());
            setField(campaign, "templateId", entity.getTemplateId());
            setField(campaign, "sequenceJson",
                    entity.getSequenceJson() != null ? new HashMap<>(entity.getSequenceJson()) : new HashMap<>());
            setField(campaign, "status", entity.getStatus());
            setField(campaign, "totalContacts", entity.getTotalContacts());
            setField(campaign, "sentCount", entity.getSentCount());
            setField(campaign, "replyCount", entity.getReplyCount());
            setField(campaign, "bounceCount", entity.getBounceCount());
            setField(campaign, "scheduledAt", entity.getScheduledAt());
            setField(campaign, "completedAt", entity.getCompletedAt());

            // AggregateRoot fields
            setField(campaign, "createdAt", entity.getCreatedAt());
            setField(campaign, "updatedAt", entity.getUpdatedAt());

            return campaign;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reconstitute Campaign aggregate from entity", e);
        }
    }

    // ── Entity ← Domain ──────────────────────────────────────────

    private CampaignEntity toEntity(Campaign campaign) {
        return CampaignEntity.builder()
                .id(campaign.getId())
                .userId(campaign.getUserId())
                .name(campaign.getName())
                .description(campaign.getDescription())
                .templateId(campaign.getTemplateId())
                .sequenceJson(campaign.getSequenceJson())
                .status(campaign.getStatus())
                .totalContacts(campaign.getTotalContacts())
                .sentCount(campaign.getSentCount())
                .replyCount(campaign.getReplyCount())
                .bounceCount(campaign.getBounceCount())
                .scheduledAt(campaign.getScheduledAt())
                .completedAt(campaign.getCompletedAt())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
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
