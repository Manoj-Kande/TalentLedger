package com.talentledger.domain.dump;

import com.talentledger.domain.shared.DomainEvent;

import java.util.UUID;

/**
 * Domain event published when a data dump has been successfully parsed
 * and its contacts have been imported.
 *
 * <p>Carries the parse result so downstream handlers can react to
 * success/failure counts without re-querying the aggregate.
 */
public final class DumpImportedEvent extends DomainEvent {

    private final UUID dumpId;
    private final UUID userId;
    private final ParseResult parseResult;

    public DumpImportedEvent(UUID dumpId, UUID userId, ParseResult parseResult) {
        this.dumpId = dumpId;
        this.userId = userId;
        this.parseResult = parseResult;
    }

    public UUID getDumpId() {
        return dumpId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ParseResult getParseResult() {
        return parseResult;
    }
}
