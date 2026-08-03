package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.LoginHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link LoginHistoryEntity}.
 */
public interface JpaLoginHistoryRepository extends JpaRepository<LoginHistoryEntity, UUID> {

    /**
     * Find login history entries for a user, ordered by most recent first.
     */
    Page<LoginHistoryEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find failed login attempts for a user.
     */
    Page<LoginHistoryEntity> findByUserIdAndSuccessFalse(UUID userId, Pageable pageable);
}
