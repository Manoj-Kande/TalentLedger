package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.SubscriptionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaSubscriptionEventRepository extends JpaRepository<SubscriptionEventEntity, UUID> {

    boolean existsByProviderEventId(String providerEventId);

    List<SubscriptionEventEntity> findBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);

    List<SubscriptionEventEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
