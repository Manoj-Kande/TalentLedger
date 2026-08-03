package com.talentledger.domain.outreach;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedListRepository {

    Optional<SavedList> findByIdAndUserId(UUID id, UUID userId);

    SavedList save(SavedList list);

    void delete(SavedList list);

    List<SavedList> findByUserId(UUID userId);
}
