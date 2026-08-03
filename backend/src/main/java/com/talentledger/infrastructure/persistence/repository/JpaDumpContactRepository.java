package com.talentledger.infrastructure.persistence.repository;

import com.talentledger.infrastructure.persistence.entity.DumpContactEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaDumpContactRepository extends JpaRepository<DumpContactEntity, UUID> {

    @Query("SELECT dc FROM DumpContactEntity dc WHERE dc.dump.id = :dumpId AND dc.deletedAt IS NULL")
    Page<DumpContactEntity> findByDumpIdAndDeletedAtIsNull(@Param("dumpId") UUID dumpId, Pageable pageable);

    @Query("SELECT dc FROM DumpContactEntity dc WHERE dc.dump.id = :dumpId AND dc.isDuplicateWithinDump = true")
    List<DumpContactEntity> findByDumpIdAndIsDuplicateWithinDumpTrue(@Param("dumpId") UUID dumpId);
}
