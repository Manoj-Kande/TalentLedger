package com.talentledger.domain.outreach;

import com.talentledger.domain.shared.BusinessRule;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OutreachEvent {

    private UUID id;
    private UUID userId;
    private UUID contactId;
    private UUID campaignId;
    private OutreachEventType eventType;
    private String status;
    private String content;
    private Sentiment sentiment;
    private Map<String, Object> metadata;
    private Instant occurredAt;
    private Instant createdAt;
    private Instant deletedAt;

    protected OutreachEvent() {}

    private OutreachEvent(UUID id, UUID userId, UUID contactId, UUID campaignId,
                           OutreachEventType eventType, String status, String content,
                           Sentiment sentiment, Map<String, Object> metadata, Instant occurredAt) {
        this.id = id;
        this.userId = userId;
        this.contactId = contactId;
        this.campaignId = campaignId;
        this.eventType = eventType;
        this.status = status;
        this.content = content;
        this.sentiment = sentiment;
        this.metadata = metadata != null ? metadata : new HashMap<>();
        this.occurredAt = occurredAt;
        this.createdAt = Instant.now();
        this.deletedAt = null;
    }

    public static OutreachEvent create(UUID userId, OutreachEventType eventType,
                                        UUID contactId, UUID campaignId,
                                        String status, String content,
                                        Sentiment sentiment, Map<String, Object> metadata,
                                        Instant occurredAt) {
        BusinessRule.ensure(userId != null, "User ID must not be null");
        BusinessRule.ensure(eventType != null, "Event type must not be null");
        Instant time = occurredAt != null ? occurredAt : Instant.now();
        return new OutreachEvent(UUID.randomUUID(), userId, contactId, campaignId,
                eventType, status, content, sentiment, metadata, time);
    }

    public void softDelete() { this.deletedAt = Instant.now(); }
    public void setContent(String content) { this.content = content; }
    public void setSentiment(Sentiment sentiment) { this.sentiment = sentiment; }
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getContactId() { return contactId; }
    public UUID getCampaignId() { return campaignId; }
    public OutreachEventType getEventType() { return eventType; }
    public String getStatus() { return status; }
    public String getContent() { return content; }
    public Sentiment getSentiment() { return sentiment; }
    public Map<String, Object> getMetadata() { return Collections.unmodifiableMap(metadata); }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
