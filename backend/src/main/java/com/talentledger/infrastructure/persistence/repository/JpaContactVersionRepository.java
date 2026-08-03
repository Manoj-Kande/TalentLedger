package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.ContactVersionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaContactVersionRepository extends JpaRepository<ContactVersionEntity, UUID> {

    Page<ContactVersionEntity> findByContactIdOrderByCreatedAtDesc(UUID contactId, Pageable pageable);
}
