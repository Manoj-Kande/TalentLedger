package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.AiEnrichmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaAiEnrichmentRepository extends JpaRepository<AiEnrichmentEntity, UUID> {

    List<AiEnrichmentEntity> findByContactIdOrderByCreatedAtDesc(UUID contactId);

    List<AiEnrichmentEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
