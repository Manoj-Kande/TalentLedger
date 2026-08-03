package com.talentledger.domain.outreach;

import com.talentledger.domain.shared.AggregateRoot;
import com.talentledger.domain.shared.BusinessRule;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Campaign aggregate root.
 *
 * <p>Represents an outreach campaign belonging to a user.
 */
public class Campaign extends AggregateRoot<UUID> {

    private UUID userId;
    private String name;
    private String description;
    private UUID templateId;
    private Map<String, Object> sequenceJson;
    private CampaignStatus status;
    private int totalContacts;
    private int sentCount;
    private int replyCount;
    private int bounceCount;
    private Instant scheduledAt;
    private Instant completedAt;

    protected Campaign() {
        // for infrastructure reconstitution
    }

    private Campaign(UUID id,
                      UUID userId,
                      String name,
                      String description,
                      UUID templateId,
                      Map<String, Object> sequenceJson,
                      CampaignStatus status,
                      int totalContacts,
                      int sentCount,
                      int replyCount,
                      int bounceCount,
                      Instant scheduledAt,
                      Instant completedAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.templateId = templateId;
        this.sequenceJson = sequenceJson != null ? sequenceJson : new HashMap<>();
        this.status = status;
        this.totalContacts = totalContacts;
        this.sentCount = sentCount;
        this.replyCount = replyCount;
        this.bounceCount = bounceCount;
        this.scheduledAt = scheduledAt;
        this.completedAt = completedAt;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Factory method to create a new campaign in DRAFT status.
     *
     * @param userId the owning user (required)
     * @param name   campaign name (required, non-blank)
     * @return a new Campaign instance
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException if arguments are invalid
     */
    public static Campaign create(UUID userId, String name) {
        BusinessRule.ensure(userId != null, "User ID must not be null");
        BusinessRule.ensure(name != null && !name.isBlank(), "Campaign name must not be blank");
        return new Campaign(
                UUID.randomUUID(),
                userId,
                name.trim(),
                null,
                null,
                new HashMap<>(),
                CampaignStatus.DRAFT,
                0, 0, 0, 0,
                null,
                null
        );
    }

    // ── State transitions ─────────────────────────────────

    public void activate() {
        this.status = CampaignStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void pause() {
        this.status = CampaignStatus.PAUSED;
        this.updatedAt = Instant.now();
    }

    public void resume() {
        this.status = CampaignStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status = CampaignStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.status = CampaignStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    // ── Counters ───────────────────────────────────────────

    public void recordSent() {
        this.sentCount++;
        this.updatedAt = Instant.now();
    }

    public void recordReply() {
        this.replyCount++;
        this.updatedAt = Instant.now();
    }

    public void recordBounce() {
        this.bounceCount++;
        this.updatedAt = Instant.now();
    }

    // ── Other mutations ────────────────────────────────────

    public void rename(String name) {
        BusinessRule.ensure(name != null && !name.isBlank(), "Campaign name must not be blank");
        this.name = name.trim();
        this.updatedAt = Instant.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
        this.updatedAt = Instant.now();
    }

    public void setSequenceJson(Map<String, Object> sequenceJson) {
        this.sequenceJson = sequenceJson != null ? sequenceJson : new HashMap<>();
        this.updatedAt = Instant.now();
    }

    public void setTotalContacts(int totalContacts) {
        BusinessRule.ensure(totalContacts >= 0, "Total contacts must not be negative");
        this.totalContacts = totalContacts;
        this.updatedAt = Instant.now();
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
        this.updatedAt = Instant.now();
    }

    // ── Getters ────────────────────────────────────────────

    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public UUID getTemplateId() { return templateId; }
    public Map<String, Object> getSequenceJson() { return Collections.unmodifiableMap(sequenceJson); }
    public CampaignStatus getStatus() { return status; }
    public int getTotalContacts() { return totalContacts; }
    public int getSentCount() { return sentCount; }
    public int getReplyCount() { return replyCount; }
    public int getBounceCount() { return bounceCount; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getCompletedAt() { return completedAt; }
}