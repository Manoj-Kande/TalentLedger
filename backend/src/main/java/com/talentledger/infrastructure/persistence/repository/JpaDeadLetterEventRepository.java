package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.DeadLetterEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaDeadLetterEventRepository extends JpaRepository<DeadLetterEventEntity, UUID> {
}
