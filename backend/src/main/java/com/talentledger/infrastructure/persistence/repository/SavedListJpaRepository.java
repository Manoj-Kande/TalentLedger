package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.SavedListEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link SavedListEntity}.
 */
public interface SavedListJpaRepository extends JpaRepository<SavedListEntity, UUID> {

    Optional<SavedListEntity> findByIdAndUserId(UUID id, UUID userId);

    List<SavedListEntity> findByUserId(UUID userId);
}
