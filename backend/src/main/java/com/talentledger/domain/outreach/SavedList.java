package com.talentledger.domain.outreach;

import com.talentledger.domain.shared.AggregateRoot;
import com.talentledger.domain.shared.BusinessRule;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SavedList extends AggregateRoot<UUID> {

    private UUID userId;
    private String name;
    private String description;
    private Map<String, Object> filtersJson;
    private boolean isDynamic;
    private int contactCount;

    protected SavedList() {}

    private SavedList(UUID id, UUID userId, String name, String description,
                       Map<String, Object> filtersJson, boolean isDynamic, int contactCount) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.filtersJson = filtersJson != null ? filtersJson : new HashMap<>();
        this.isDynamic = isDynamic;
        this.contactCount = contactCount;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public static SavedList create(UUID userId, String name) {
        BusinessRule.ensure(userId != null, "User ID must not be null");
        BusinessRule.ensure(name != null && !name.isBlank(), "List name must not be blank");
        return new SavedList(UUID.randomUUID(), userId, name.trim(), null,
                new HashMap<>(), false, 0);
    }

    public void rename(String name) {
        BusinessRule.ensure(name != null && !name.isBlank(), "List name must not be blank");
        this.name = name.trim();
        this.updatedAt = Instant.now();
    }

    public void setFilters(Map<String, Object> filters) {
        this.filtersJson = filters != null ? filters : new HashMap<>();
        this.updatedAt = Instant.now();
    }

    public void incrementContactCount() {
        this.contactCount++;
        this.updatedAt = Instant.now();
    }

    public void decrementContactCount() {
        BusinessRule.ensure(this.contactCount > 0, "Contact count must not go negative");
        this.contactCount--;
        this.updatedAt = Instant.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void setDynamic(boolean dynamic) {
        this.isDynamic = dynamic;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, Object> getFiltersJson() { return Collections.unmodifiableMap(filtersJson); }
    public boolean isDynamic() { return isDynamic; }
    public int getContactCount() { return contactCount; }
}
