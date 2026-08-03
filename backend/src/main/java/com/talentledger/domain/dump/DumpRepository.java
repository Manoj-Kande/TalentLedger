package com.talentledger.domain.dump;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for the DataDump aggregate.
 *
 * <p>Pure Java interface — no framework imports.
 * Implementations live in the infrastructure (outbound) adapter layer.
 */
public interface DumpRepository {

    Optional<DataDump> findByIdAndUserId(UUID id, UUID userId);
    Optional<DataDump> findById(UUID id);
    DataDump save(DataDump dump);
    void delete(DataDump dump);
    boolean existsByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndFileHash(UUID userId, String fileHash);
    List<DataDump> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<DataDump> findExpiredFreeDumps(Instant now);
}
