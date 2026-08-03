package com.talentledger.application.dto.request;

import java.util.List;
import java.util.UUID;

public record BulkContactRequest(
    String operation, // ADD_TO_LIST, EXPORT, DELETE, ARCHIVE
    List<UUID> contactIds,
    UUID listId // for ADD_TO_LIST
) {}
