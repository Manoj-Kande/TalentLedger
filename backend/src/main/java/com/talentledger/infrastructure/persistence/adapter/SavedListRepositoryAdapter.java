package com.talentledger.infrastructure.persistence.adapter;

import com.talentledger.domain.outreach.SavedList;
import com.talentledger.domain.outreach.SavedListRepository;
import com.talentledger.infrastructure.persistence.entity.SavedListEntity;
import com.talentledger.infrastructure.persistence.repository.SavedListJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that bridges the {@link SavedListRepository} domain port
 * to the Spring Data JPA {@link SavedListJpaRepository}.
 *
 * <p>{@link SavedList} has a protected no-arg constructor for infrastructure
 * reconstitution, but it resides in a different package, so reflection is used.
 */
@Component
@RequiredArgsConstructor
public class SavedListRepositoryAdapter implements SavedListRepository {

    private final SavedListJpaRepository jpaRepository;

    // ── Query methods ────────────────────────────────────────────

    @Override
    public Optional<SavedList> findByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    public List<SavedList> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    // ── Mutation methods ──────────────────────────────────────────

    @Override
    @Transactional
    public SavedList save(SavedList list) {
        SavedListEntity entity = toEntity(list);
        SavedListEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public void delete(SavedList list) {
        SavedListEntity entity = toEntity(list);
        jpaRepository.delete(entity);
    }

    // ── Domain ← Entity ──────────────────────────────────────────

    private SavedList toDomain(SavedListEntity entity) {
        try {
            Constructor<SavedList> ctor = SavedList.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            SavedList list = ctor.newInstance();

            setField(list, "id", entity.getId());
            setField(list, "userId", entity.getUserId());
            setField(list, "name", entity.getName());
            setField(list, "description", entity.getDescription());
            setField(list, "filtersJson",
                    entity.getFiltersJson() != null ? new HashMap<>(entity.getFiltersJson()) : new HashMap<>());
            setField(list, "isDynamic", toBool(entity.getIsDynamic()));
            setField(list, "contactCount", entity.getContactCount());

            // AggregateRoot fields
            setField(list, "createdAt", entity.getCreatedAt());
            setField(list, "updatedAt", entity.getUpdatedAt());
            setField(list, "deletedAt", entity.getDeletedAt());

            return list;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reconstitute SavedList aggregate from entity", e);
        }
    }

    // ── Entity ← Domain ──────────────────────────────────────────

    private SavedListEntity toEntity(SavedList list) {
        return SavedListEntity.builder()
                .id(list.getId())
                .userId(list.getUserId())
                .name(list.getName())
                .description(list.getDescription())
                .filtersJson(list.getFiltersJson())
                .isDynamic(list.isDynamic())
                .contactCount(list.getContactCount())
                .createdAt(list.getCreatedAt())
                .updatedAt(list.getUpdatedAt())
                .deletedAt(list.getDeletedAt())
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
