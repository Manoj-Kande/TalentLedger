package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.CampaignEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CampaignEntity}.
 */
public interface CampaignJpaRepository extends JpaRepository<CampaignEntity, UUID> {

    Optional<CampaignEntity> findByIdAndUserId(UUID id, UUID userId);

    List<CampaignEntity> findByUserId(UUID userId);
}
