package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {

    Optional<ApiKeyEntity> findByKeyHashAndRevokedAtIsNull(String keyHash);

    List<ApiKeyEntity> findByUserIdAndRevokedAtIsNull(UUID userId);
}
