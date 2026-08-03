package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.CampaignEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaCampaignRepository extends JpaRepository<CampaignEntity, UUID> {

    List<CampaignEntity> findByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<CampaignEntity> findByIdAndUserId(UUID id, UUID userId);

    long countByStatusAndDeletedAtIsNull(com.talentledger.domain.outreach.CampaignStatus status);
}
