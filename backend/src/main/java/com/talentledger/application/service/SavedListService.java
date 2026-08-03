package com.talentledger.application.service;

import com.talentledger.application.dto.response.ContactResponse;
import com.talentledger.application.dto.response.SavedListResponse;
import com.talentledger.application.port.inbound.SavedListUseCase;
import com.talentledger.application.port.inbound.SavedListUseCase.BulkAddResult;
import com.talentledger.application.port.inbound.SavedListUseCase.CreateListCommand;
import com.talentledger.application.port.inbound.SavedListUseCase.ListContactsResult;
import com.talentledger.application.port.inbound.SavedListUseCase.SavedListDetailResult;
import com.talentledger.application.port.inbound.SavedListUseCase.UpdateListCommand;
import com.talentledger.domain.outreach.SavedList;
import com.talentledger.domain.outreach.SavedListRepository;
import com.talentledger.domain.shared.Result;
import com.talentledger.infrastructure.persistence.entity.ContactEntity;
import com.talentledger.infrastructure.persistence.entity.SavedListContactEntity;
import com.talentledger.infrastructure.persistence.entity.SavedListEntity;
import com.talentledger.infrastructure.persistence.repository.ContactJpaRepository;
import com.talentledger.infrastructure.persistence.repository.JpaSavedListContactRepository;
import com.talentledger.infrastructure.persistence.repository.SavedListJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavedListService implements SavedListUseCase {

    private final SavedListRepository savedListRepository;
    private final JpaSavedListContactRepository savedListContactRepository;
    private final ContactJpaRepository contactJpaRepository;

    @Override
    public Result<List<SavedListResponse>, String> listLists(UUID userId) {
        List<SavedList> lists = savedListRepository.findByUserId(userId);
        List<SavedListResponse> responses = lists.stream().map(this::toResponse).toList();
        return Result.success(responses);
    }

    @Override
    public Result<SavedListDetailResult, String> getList(UUID listId, UUID userId) {
        return savedListRepository.findByIdAndUserId(listId, userId)
                .map(list -> {
                    List<SavedListContactEntity> junctions = savedListContactRepository.findByList_Id(listId);
                    List<ContactResponse> contacts = junctions.stream()
                            .map(jc -> contactJpaRepository.findByIdAndUserId(jc.getContactId(), userId))
                            .filter(Optional::isPresent)
                            .map(opt -> toContactResponse(opt.get()))
                            .toList();
                    return Result.<SavedListDetailResult, String>success(
                            new SavedListDetailResult(toResponse(list), contacts, null, false));
                })
                .orElseGet(() -> Result.failure("List not found"));
    }

    @Override
    @Transactional
    public Result<SavedListResponse, String> createList(UUID userId, CreateListCommand command) {
        SavedList list = SavedList.create(userId, command.name());
        if (command.description() != null) {
            list.setDescription(command.description());
        }
        SavedList saved = savedListRepository.save(list);
        return Result.success(toResponse(saved));
    }

    @Override
    @Transactional
    public Result<SavedListResponse, String> updateList(UUID listId, UUID userId, UpdateListCommand command) {
        return savedListRepository.findByIdAndUserId(listId, userId)
                .map(list -> {
                    if (command.name() != null) list.rename(command.name());
                    if (command.description() != null) list.setDescription(command.description());
                    if (command.isDynamic() != null) list.setDynamic(command.isDynamic());
                    SavedList saved = savedListRepository.save(list);
                    return Result.<SavedListResponse, String>success(toResponse(saved));
                })
                .orElseGet(() -> Result.failure("List not found"));
    }

    @Override
    @Transactional
    public Result<Void, String> deleteList(UUID listId, UUID userId) {
        return savedListRepository.findByIdAndUserId(listId, userId)
                .<Result<Void, String>>map(list -> {
                    savedListContactRepository.deleteAllByList_Id(listId);
                    savedListRepository.delete(list);
                    log.info("List {} deleted by user {}", listId, userId);
                    return Result.success(null);
                })
                .orElseGet(() -> Result.failure("List not found"));
    }

    @Override
    @Transactional
    public Result<BulkAddResult, String> addContactsToList(UUID listId, UUID userId, List<UUID> contactIds) {
        return savedListRepository.findByIdAndUserId(listId, userId)
                .<Result<BulkAddResult, String>>map(list -> {
                    int added = 0;
                    int skipped = 0;
                    for (UUID contactId : contactIds) {
                        if (!contactJpaRepository.existsByIdAndUserId(contactId, userId)) {
                            skipped++;
                            continue;
                        }
                        if (savedListContactRepository.existsByList_IdAndContactId(listId, contactId)) {
                            skipped++;
                            continue;
                        }

                        SavedListEntity listEntity = new SavedListEntity();
                        listEntity.setId(listId);

                        SavedListContactEntity junction = SavedListContactEntity.builder()
                                .list(listEntity)
                                .contactId(contactId)
                                .addedAt(Instant.now())
                                .addedReason("manual")
                                .build();
                        savedListContactRepository.save(junction);
                        added++;
                    }
                    for (int i = 0; i < added; i++) {
                        list.incrementContactCount();
                    }
                    savedListRepository.save(list);
                    log.info("Added {} contacts to list {} by user {} (skipped {})", added, listId, userId, skipped);
                    return Result.success(new BulkAddResult(added, skipped, 0));
                })
                .orElseGet(() -> Result.failure("List not found"));
    }

    @Override
    @Transactional
    public Result<Void, String> removeContactFromList(UUID listId, UUID userId, UUID contactId) {
        return savedListRepository.findByIdAndUserId(listId, userId)
                .<Result<Void, String>>map(list -> {
                    savedListContactRepository.deleteByList_IdAndContactId(listId, contactId);
                    list.decrementContactCount();
                    savedListRepository.save(list);
                    log.info("Removed contact {} from list {} by user {}", contactId, listId, userId);
                    return Result.success(null);
                })
                .orElseGet(() -> Result.failure("List not found"));
    }

    @Override
    public Result<ListContactsResult, String> getListContacts(UUID listId, UUID userId, String cursor, int size) {
        if (!savedListRepository.findByIdAndUserId(listId, userId).isPresent()) {
            return Result.failure("List not found");
        }

        var pageable = org.springframework.data.domain.PageRequest.of(0, size + 1);
        List<SavedListContactEntity> junctions;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = cursor.split("\\|");
                var cursorAt = Instant.parse(parts[0]);
                UUID cursorContactId = UUID.fromString(parts[1]);
                junctions = savedListContactRepository.findByListIdCursor(listId, cursorAt, cursorContactId, pageable);
            } catch (Exception e) {
                log.warn("Invalid list-contacts cursor, falling back to first page: {}", cursor);
                junctions = savedListContactRepository.findByListIdNewest(listId, pageable);
            }
        } else {
            junctions = savedListContactRepository.findByListIdNewest(listId, pageable);
        }

        boolean hasMore = junctions.size() > size;
        List<SavedListContactEntity> page = hasMore ? junctions.subList(0, size) : junctions;

        List<ContactResponse> contacts = page.stream()
                .map(jc -> contactJpaRepository.findByIdAndUserId(jc.getContactId(), userId))
                .filter(Optional::isPresent)
                .map(opt -> toContactResponse(opt.get()))
                .toList();

        String nextCursor = (hasMore && !page.isEmpty())
                ? page.get(page.size() - 1).getAddedAt() + "|" + page.get(page.size() - 1).getContactId()
                : null;

        return Result.success(new ListContactsResult(contacts, nextCursor, hasMore));
    }

    private SavedListResponse toResponse(SavedList list) {
        return new SavedListResponse(
                list.getId(), list.getName(), list.getDescription(),
                list.isDynamic(), list.getContactCount(),
                list.getCreatedAt(), list.getUpdatedAt());
    }

    private ContactResponse toContactResponse(ContactEntity e) {
        return new ContactResponse(
                e.getId(), e.getName(),
                e.getEmail(), e.getNormalizedEmail(),
                e.getPhone(), e.getLinkedinUrl(), e.getSecondaryEmail(),
                e.getTitle(), e.getDepartment(),
                e.getSeniorityLevel() != null ? e.getSeniorityLevel().name() : null,
                e.getLocation(), e.getTimezone(), e.getLanguage(),
                e.getDomain(), e.getVerificationScore(), e.getSource(),
                e.getPrimaryDumpId(), e.getCompanyId(), null,
                e.getNotes(),
                e.getTags() != null ? new ArrayList<>(e.getTags()) : new ArrayList<>(),
                e.getCustomFields() != null ? new HashMap<>(e.getCustomFields()) : new HashMap<>(),
                e.getAiEnrichment() != null ? new HashMap<>(e.getAiEnrichment()) : new HashMap<>(),
                e.getStatus() != null ? e.getStatus().name() : null,
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
