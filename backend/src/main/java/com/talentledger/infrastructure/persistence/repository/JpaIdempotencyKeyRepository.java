package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaIdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, String> {

    Optional<IdempotencyKeyEntity> findByKeyHash(String keyHash);
}
