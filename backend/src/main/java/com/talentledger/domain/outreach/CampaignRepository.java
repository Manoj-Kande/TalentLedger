package com.talentledger.domain.outreach;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository {

    Optional<Campaign> findByIdAndUserId(UUID id, UUID userId);

    Campaign save(Campaign campaign);

    void delete(Campaign campaign);

    List<Campaign> findByUserId(UUID userId);
}
