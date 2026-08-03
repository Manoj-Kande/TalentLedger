package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.PasswordHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaPasswordHistoryRepository extends JpaRepository<PasswordHistoryEntity, UUID> {

    Page<PasswordHistoryEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
