package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.MfaBackupCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaMfaBackupCodeRepository extends JpaRepository<MfaBackupCodeEntity, UUID> {

    List<MfaBackupCodeEntity> findByUserIdAndUsedAtIsNull(UUID userId);

    java.util.Optional<MfaBackupCodeEntity> findByUserIdAndCodeHashAndUsedAtIsNull(UUID userId, String codeHash);

    void deleteByUserId(UUID userId);
}
