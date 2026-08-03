package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.SavedListContactEntity;
import com.talentledger.infrastructure.persistence.entity.SavedListContactEntity.SavedListContactId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link SavedListContactEntity} junction table.
 */
public interface JpaSavedListContactRepository extends JpaRepository<SavedListContactEntity, SavedListContactId> {

    @Query("SELECT slc FROM SavedListContactEntity slc WHERE slc.list.id = :listId ORDER BY slc.addedAt DESC")
    Page<SavedListContactEntity> findByListId(@Param("listId") UUID listId, Pageable pageable);

    @Query("SELECT slc FROM SavedListContactEntity slc WHERE slc.list.id = :listId " +
           "AND (slc.addedAt < :cursorAt OR (slc.addedAt = :cursorAt AND slc.contactId < :cursorContactId)) " +
           "ORDER BY slc.addedAt DESC, slc.contactId DESC")
    List<SavedListContactEntity> findByListIdCursor(@Param("listId") UUID listId,
                                                    @Param("cursorAt") java.time.Instant cursorAt,
                                                    @Param("cursorContactId") UUID cursorContactId,
                                                    Pageable pageable);

    @Query("SELECT slc FROM SavedListContactEntity slc WHERE slc.list.id = :listId ORDER BY slc.addedAt DESC, slc.contactId DESC")
    List<SavedListContactEntity> findByListIdNewest(@Param("listId") UUID listId, Pageable pageable);

    boolean existsByList_IdAndContactId(UUID listId, UUID contactId);

    void deleteByList_IdAndContactId(UUID listId, UUID contactId);

    void deleteAllByList_Id(UUID listId);

    long countByList_Id(UUID listId);

    List<SavedListContactEntity> findByList_Id(UUID listId);
}
