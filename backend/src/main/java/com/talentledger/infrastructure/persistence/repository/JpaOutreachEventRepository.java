package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.OutreachEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaOutreachEventRepository extends JpaRepository<OutreachEventEntity, UUID> {

    List<OutreachEventEntity> findByUserIdAndDeletedAtIsNull(UUID userId);

    List<OutreachEventEntity> findByContactIdAndDeletedAtIsNullOrderByOccurredAtDesc(UUID contactId);
}
