package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.UserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaUserDeviceRepository extends JpaRepository<UserDeviceEntity, UUID> {

    List<UserDeviceEntity> findByUserIdOrderByLastSeenAtDesc(UUID userId);
}
