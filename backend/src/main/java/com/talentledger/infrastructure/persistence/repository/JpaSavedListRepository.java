package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.SavedListEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaSavedListRepository extends JpaRepository<SavedListEntity, UUID> {

    List<SavedListEntity> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<SavedListEntity> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
