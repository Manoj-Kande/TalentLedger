package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface JpaOutboxEventRepository extends JpaRepository<OutboxEventEntity, java.util.UUID> {

    Page<OutboxEventEntity> findByPublishedFalseAndCreatedAtBefore(Instant before, Pageable pageable);

    Page<OutboxEventEntity> findByPublishedFalseOrderByCreatedAtAsc(Pageable pageable);
}
