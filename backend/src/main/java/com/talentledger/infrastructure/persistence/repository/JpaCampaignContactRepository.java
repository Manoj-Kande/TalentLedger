package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.CampaignContactEntity;
import com.talentledger.infrastructure.persistence.entity.CampaignContactEntity.CampaignContactId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CampaignContactEntity} junction table.
 */
public interface JpaCampaignContactRepository extends JpaRepository<CampaignContactEntity, CampaignContactId> {

    @Query("SELECT cc FROM CampaignContactEntity cc WHERE cc.campaignId = :campaignId ORDER BY cc.contactId")
    Page<CampaignContactEntity> findByCampaignId(@Param("campaignId") UUID campaignId, Pageable pageable);

    @Query("SELECT cc FROM CampaignContactEntity cc WHERE cc.campaignId = :campaignId AND cc.contactId > :cursorContactId " +
           "ORDER BY cc.contactId")
    List<CampaignContactEntity> findByCampaignIdCursor(@Param("campaignId") UUID campaignId,
                                                       @Param("cursorContactId") UUID cursorContactId,
                                                       Pageable pageable);

    boolean existsByCampaignIdAndContactId(UUID campaignId, UUID contactId);

    void deleteByCampaignIdAndContactId(UUID campaignId, UUID contactId);

    void deleteAllByCampaignId(UUID campaignId);

    long countByCampaignId(UUID campaignId);
}
