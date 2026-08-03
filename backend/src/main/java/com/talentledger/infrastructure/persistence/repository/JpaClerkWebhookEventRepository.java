package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.ClerkWebhookEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaClerkWebhookEventRepository extends JpaRepository<ClerkWebhookEventEntity, java.util.UUID> {

    Page<ClerkWebhookEventEntity> findByProcessedFalseOrderByCreatedAtAsc(Pageable pageable);

    boolean existsByClerkEventId(String clerkEventId);
}
