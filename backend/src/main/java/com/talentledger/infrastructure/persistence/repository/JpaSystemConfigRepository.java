package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.SystemConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaSystemConfigRepository extends JpaRepository<SystemConfigEntity, String> {

    Optional<SystemConfigEntity> findById(String key);
}
