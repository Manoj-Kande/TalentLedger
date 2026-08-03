package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.UserAuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaAuditLogRepository extends JpaRepository<UserAuditLogEntity, UUID> {

    Page<UserAuditLogEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
