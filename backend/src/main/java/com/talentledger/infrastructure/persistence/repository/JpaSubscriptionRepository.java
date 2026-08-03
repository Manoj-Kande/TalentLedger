package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.SubscriptionEntity;
import com.talentledger.infrastructure.persistence.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaSubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    // NOTE: previously declared as (UUID, String) -- status is a SubscriptionStatus
    // enum column, so binding a raw String parameter into that comparison is a
    // type mismatch Hibernate would reject at query-validation time. Fixed to
    // take the enum directly.
    List<SubscriptionEntity> findByUserIdAndStatus(UUID userId, SubscriptionStatus status);

    List<SubscriptionEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<SubscriptionEntity> findByProviderSubscriptionId(String providerSubscriptionId);

    Optional<SubscriptionEntity> findFirstByProviderCustomerIdOrderByCreatedAtDesc(String providerCustomerId);
}
