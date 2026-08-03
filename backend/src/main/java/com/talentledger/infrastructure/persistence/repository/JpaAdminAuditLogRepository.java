package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.AdminAuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaAdminAuditLogRepository extends JpaRepository<AdminAuditLogEntity, UUID> {

    Page<AdminAuditLogEntity> findByTargetUserIdOrderByCreatedAtDesc(UUID targetUserId, Pageable pageable);
}
