package com.talentledger.domain.shared;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all aggregate roots.
 *
 * <p>Provides identity, domain event collection, and soft-delete tracking.
 * Pure Java — zero framework dependency.
 *
 * @param <ID> the type of the aggregate's primary identifier
 */
public abstract class AggregateRoot<ID> {

    protected ID id;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected Instant createdAt;
    protected Instant updatedAt;
    protected Instant deletedAt;

    protected AggregateRoot() {}

    protected AggregateRoot(ID id) {
        this.id = id;
    }

    public ID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    // ── Domain Events ──────────────────────────────────────

    /** Register a domain event to be published after the transaction commits. */
    protected void registerEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    /** Clear all collected events (called by the outbox infrastructure). */
    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    /** Return an unmodifiable view of collected domain events. */
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    // ── Soft Delete ─────────────────────────────────────────

    /** Mark this aggregate as soft-deleted at the given instant. */
    public void softDelete(Instant deletedAt) {
        this.deletedAt = deletedAt;
        this.updatedAt = deletedAt;
    }

    /** Restore a soft-deleted aggregate. */
    public void restore() {
        this.deletedAt = null;
        this.updatedAt = Instant.now();
    }
}
