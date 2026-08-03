package com.talentledger.infrastructure.persistence.adapter;

import com.talentledger.domain.dump.ColumnMapping;
import com.talentledger.domain.dump.DataDump;
import com.talentledger.domain.dump.DumpRepository;
import com.talentledger.infrastructure.persistence.entity.DataDumpEntity;
import com.talentledger.infrastructure.persistence.repository.JpaDataDumpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that bridges the {@link DumpRepository} domain port
 * to the Spring Data JPA {@link com.talentledger.infrastructure.persistence.repository.JpaDataDumpRepository}.
 *
 * <p>The {@link DataDump} domain aggregate has a private constructor and public
 * setters for most infrastructure fields. Fields not exposed via setters
 * (id, userId, name, originalFilename, fileType, fileSizeBytes, isPinned,
 * isArchived) are set via reflection.
 */
@Component
@RequiredArgsConstructor
public class DumpRepositoryAdapter implements DumpRepository {

    private final JpaDataDumpRepository jpaRepository;

    // ── Query methods ────────────────────────────────────────────

    @Override
    public Optional<DataDump> findByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    public Optional<DataDump> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.existsByIdAndUserId(id, userId);
    }

    @Override
    public boolean existsByUserIdAndFileHash(UUID userId, String fileHash) {
        return jpaRepository.existsByUserIdAndFileHash(userId, fileHash);
    }

    @Override
    public List<DataDump> findByUserIdOrderByCreatedAtDesc(UUID userId) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<DataDump> findExpiredFreeDumps(Instant now) {
        return jpaRepository.findExpiredFreeDumps(now)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // ── Mutation methods ──────────────────────────────────────────

    @Override
    @Transactional
    public DataDump save(DataDump dump) {
        DataDumpEntity entity = toEntity(dump);
        DataDumpEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public void delete(DataDump dump) {
        DataDumpEntity entity = toEntity(dump);
        jpaRepository.delete(entity);
    }

    // ── Domain ← Entity ──────────────────────────────────────────

    private DataDump toDomain(DataDumpEntity entity) {
        try {
            // Use the private constructor DataDump(UUID id)
            var ctor = DataDump.class.getDeclaredConstructor(UUID.class);
            ctor.setAccessible(true);
            DataDump dump = ctor.newInstance(entity.getId());

            // Fields with no public setters — set via reflection
            setField(dump, "userId", entity.getUserId());
            setField(dump, "name", entity.getName());
            setField(dump, "originalFilename", entity.getOriginalFilename());
            setField(dump, "fileType", entity.getFileType());
            setField(dump, "fileSizeBytes", entity.getFileSizeBytes());
            setField(dump, "isPinned", toBool(entity.getIsPinned()));
            setField(dump, "isArchived", toBool(entity.getIsArchived()));

            // Fields with public setters
            dump.setDescription(entity.getDescription());
            dump.setTags(entity.getTags() != null ? new ArrayList<>(entity.getTags()) : new ArrayList<>());
            dump.setFileHash(entity.getFileHash());
            dump.setStatus(entity.getStatus());
            dump.setTotalRows(entity.getTotalRows());
            dump.setParsedContactsCount(entity.getParsedContactsCount());
            dump.setLiveContactsCount(entity.getLiveContactsCount());
            dump.setDuplicateWithinDumpCount(entity.getDuplicateWithinDumpCount());
            dump.setCrossDumpDuplicateCount(entity.getCrossDumpDuplicateCount());
            dump.setErrorCount(entity.getErrorCount());
            dump.setParseErrors(entity.getParseErrors() != null
                    ? new ArrayList<>(entity.getParseErrors()) : new ArrayList<>());
            // parseDurationMs is nullable in the DB (unset until parsing finishes);
            // DataDump.parseDurationMs is a primitive int, so default null -> 0.
            dump.setParseDurationMs(entity.getParseDurationMs() != null ? entity.getParseDurationMs() : 0);
            dump.setPersisted(toBool(entity.getIsPersisted()));
            dump.setStorageProvider(entity.getStorageProvider());
            dump.setOriginalFileKey(entity.getOriginalFileKey());
            dump.setParsedSnapshotKey(entity.getParsedSnapshotKey());
            dump.setOriginalFileDeletedAt(entity.getOriginalFileDeletedAt());
            dump.setExpiresAt(entity.getExpiresAt());
            dump.setCompletedAt(entity.getCompletedAt());

            // Reconstitute ColumnMapping from JSONB map
            if (entity.getColumnMapping() != null && !entity.getColumnMapping().isEmpty()) {
                @SuppressWarnings("unchecked")
                List<String> detectedHeaders = (List<String>) entity.getColumnMapping().getOrDefault("detectedHeaders", List.of());
                @SuppressWarnings("unchecked")
                Map<String, String> mappedFields = (Map<String, String>) entity.getColumnMapping().getOrDefault("mappedFields", Map.of());
                @SuppressWarnings("unchecked")
                List<String> unmappedHeaders = (List<String>) entity.getColumnMapping().getOrDefault("unmappedHeaders", List.of());
                Object confidenceObj = entity.getColumnMapping().get("confidence");

                ColumnMapping.Builder cmBuilder = ColumnMapping.builder()
                        .detectedHeaders(detectedHeaders)
                        .mappedFields(mappedFields)
                        .unmappedHeaders(unmappedHeaders);
                if (confidenceObj instanceof Number) {
                    cmBuilder.confidence(((Number) confidenceObj).doubleValue());
                }
                dump.setColumnMapping(cmBuilder.build());
            }

            // AggregateRoot fields
            setField(dump, "createdAt", entity.getCreatedAt());
            setField(dump, "updatedAt", entity.getUpdatedAt());
            setField(dump, "deletedAt", entity.getDeletedAt());

            return dump;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reconstitute DataDump aggregate from entity", e);
        }
    }

    // ── Entity ← Domain ──────────────────────────────────────────

    private DataDumpEntity toEntity(DataDump dump) {
        // Convert ColumnMapping to JSONB-compatible map
        Map<String, Object> columnMappingMap = new HashMap<>();
        if (dump.getColumnMapping() != null) {
            columnMappingMap.put("detectedHeaders", dump.getColumnMapping().getDetectedHeaders());
            columnMappingMap.put("mappedFields", dump.getColumnMapping().getMappedFields());
            columnMappingMap.put("confidence", dump.getColumnMapping().getConfidence());
            columnMappingMap.put("unmappedHeaders", dump.getColumnMapping().getUnmappedHeaders());
        }

        return DataDumpEntity.builder()
                .id(dump.getId())
                .userId(dump.getUserId())
                .name(dump.getName())
                .description(dump.getDescription())
                .tags(dump.getTags())
                .originalFilename(dump.getOriginalFilename())
                .fileType(dump.getFileType())
                .fileSizeBytes(dump.getFileSizeBytes())
                .fileHash(dump.getFileHash())
                .columnMapping(columnMappingMap)
                .status(dump.getStatus())
                .totalRows(dump.getTotalRows())
                .parsedContactsCount(dump.getParsedContactsCount())
                .liveContactsCount(dump.getLiveContactsCount())
                .duplicateWithinDumpCount(dump.getDuplicateWithinDumpCount())
                .crossDumpDuplicateCount(dump.getCrossDumpDuplicateCount())
                .errorCount(dump.getErrorCount())
                .parseErrors(dump.getParseErrors())
                .parseDurationMs(dump.getParseDurationMs())
                .isPinned(dump.isPinned())
                .isArchived(dump.isArchived())
                .isPersisted(dump.isPersisted())
                .storageProvider(dump.getStorageProvider())
                .originalFileKey(dump.getOriginalFileKey())
                .parsedSnapshotKey(dump.getParsedSnapshotKey())
                .originalFileDeletedAt(dump.getOriginalFileDeletedAt())
                .expiresAt(dump.getExpiresAt())
                .completedAt(dump.getCompletedAt())
                .createdAt(dump.getCreatedAt())
                .updatedAt(dump.getUpdatedAt())
                .deletedAt(dump.getDeletedAt())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Walk up the class hierarchy to find a declared field. */
    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + clazz.getName() + " hierarchy");
    }

    private static boolean toBool(Boolean v) {
        return v != null && v;
    }
}
