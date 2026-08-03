package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.EmailVerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface JpaEmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, UUID> {

    Optional<EmailVerificationTokenEntity> findByTokenHashAndVerifiedAtIsNullAndExpiresAtAfter(String tokenHash, Instant now);
}
