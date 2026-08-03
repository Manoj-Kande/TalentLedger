package com.talentledger.domain.contact;

import com.talentledger.domain.shared.AggregateRoot;
import com.talentledger.domain.shared.BusinessRule;
import com.talentledger.domain.shared.DomainEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Contact aggregate root — the central entity of the Contact bounded context.
 *
 * <p>Represents a person in the user's professional network. Extends
 * {@link AggregateRoot} to inherit identity, domain-event collection, and
 * soft-delete tracking.
 *
 * <p>Pure Java — zero framework annotations. Use the static factory method
 * {@link #create} to instantiate.
 */
public final class Contact extends AggregateRoot<UUID> {

    // ── Constants ───────────────────────────────────────────

    private static final int MAX_LINKEDIN_URL_LENGTH = 500;
    private static final int MAX_DEPARTMENT_LENGTH = 100;
    private static final int MIN_VERIFICATION_SCORE = 0;
    private static final int MAX_VERIFICATION_SCORE = 100;
    private static final int VERIFIED_THRESHOLD = 70;
    private static final String DEFAULT_SOURCE = "csv";

    // ── Fields ──────────────────────────────────────────────

    private UUID userId;
    private UUID primaryDumpId;
    private UUID companyId;
    private String name;
    private Email email;
    private NormalizedEmail normalizedEmail;
    private String phone;
    private String linkedinUrl;
    private String secondaryEmail;
    private String title;
    private String department;
    private SeniorityLevel seniorityLevel;
    private String location;
    private String timezone;
    private String language;
    private String domain;
    private int verificationScore;
    private LocalDate lastActivityDate;
    private String sourceUrl;
    private String source;
    private String notes;
    private List<String> tags;
    private Map<String, Object> customFields;
    private Map<String, Object> aiEnrichment;
    private ContactStatus status;

    // ── Constructor ─────────────────────────────────────────

    private Contact(UUID id) {
        super(id);
        this.tags = new ArrayList<>();
        this.customFields = new HashMap<>();
        this.aiEnrichment = new HashMap<>();
        this.status = ContactStatus.ACTIVE;
        this.verificationScore = 0;
        this.source = DEFAULT_SOURCE;
    }

    // ── Factory ───────────────────────────────────────────

    /**
     * Create a new Contact aggregate.
     *
     * @param userId the owning user's id (must not be null)
     * @param name   the contact's display name (must not be blank)
     * @param email  a validated Email value object (must not be null)
     * @return a fully initialised Contact
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException
     *     on any rule violation
     */
    public static Contact create(UUID userId, String name, Email email) {
        return create(userId, name, email, builder -> {});
    }

    /**
     * Create a new Contact aggregate with optional fields configured via the
     * provided builder consumer.
     *
     * @param userId the owning user's id
     * @param name   display name
     * @param email  validated email
     * @param config additional field configuration (may be empty)
     * @return a fully initialised Contact
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException
     *     on any rule violation
     */
    public static Contact create(UUID userId, String name, Email email,
                                 Consumer<ContactBuilder> config) {
        BusinessRule.notNull(userId, "User ID");

        UUID contactId = UUID.randomUUID();
        Contact contact = new Contact(contactId);

        contact.userId = userId;
        contact.name = name;
        contact.email = email;
        contact.normalizedEmail = NormalizedEmail.of(email.getNormalized());
        contact.domain = email.getDomain();

        // Apply optional configuration
        ContactBuilder builder = new ContactBuilder();
        config.accept(builder);
        builder.applyTo(contact);

        // Validate invariants after all fields are set
        contact.validateInvariants();

        Instant now = Instant.now();
        contact.createdAt = now;
        contact.updatedAt = now;
        contact.lastActivityDate = LocalDate.now();

        contact.registerEvent(new ContactCreatedEvent(contactId, userId, email.getNormalized()));

        return contact;
    }

    // ── Invariant Validation ───────────────────────────────

    private void validateInvariants() {
        BusinessRule.notNull(name, "Name");
        BusinessRule.ensure(!name.isBlank(), "Name must not be blank");
        BusinessRule.notNull(email, "Email");
        BusinessRule.notNull(normalizedEmail, "Normalized email");
        BusinessRule.notNull(domain, "Domain");

        if (linkedinUrl != null) {
            BusinessRule.ensure(
                    linkedinUrl.length() <= MAX_LINKEDIN_URL_LENGTH,
                    "LinkedIn URL must not exceed %d characters", MAX_LINKEDIN_URL_LENGTH);
        }

        if (department != null) {
            BusinessRule.ensure(
                    department.length() <= MAX_DEPARTMENT_LENGTH,
                    "Department must not exceed %d characters", MAX_DEPARTMENT_LENGTH);
        }

        BusinessRule.ensure(
                verificationScore >= MIN_VERIFICATION_SCORE
                        && verificationScore <= MAX_VERIFICATION_SCORE,
                "Verification score must be between %d and %d",
                MIN_VERIFICATION_SCORE, MAX_VERIFICATION_SCORE);
    }

    // ── Partial Update ──────────────────────────────────────

    /**
     * Apply partial updates via a builder-style consumer. Only the fields
     * explicitly set through the builder will be changed.
     *
     * @param updater a consumer that configures which fields to change
     */
    public void updateFields(Consumer<ContactBuilder> updater) {
        ContactBuilder builder = new ContactBuilder();
        updater.accept(builder);
        builder.applyTo(this);
        validateInvariants();
        this.updatedAt = Instant.now();
        registerEvent(new ContactUpdatedEvent(this.id, this.userId));
    }

    // ── Lifecycle ───────────────────────────────────────────

    /** Archive this contact — hides it from default views. */
    public void archive() {
        BusinessRule.ensure(
                this.status != ContactStatus.DELETED,
                "Cannot archive a deleted contact; restore it first");
        this.status = ContactStatus.ARCHIVED;
        this.updatedAt = Instant.now();
        registerEvent(new ContactArchivedEvent(this.id, this.userId));
    }

    /** Soft-delete this contact. */
    public void softDelete() {
        this.status = ContactStatus.DELETED;
        this.updatedAt = Instant.now();
        super.softDelete(Instant.now());
        registerEvent(new ContactSoftDeletedEvent(this.id, this.userId));
    }

    /** Restore a soft-deleted or archived contact to active status. */
    public void restore() {
        super.restore();
        this.status = ContactStatus.ACTIVE;
        this.updatedAt = Instant.now();
        registerEvent(new ContactRestoredEvent(this.id, this.userId));
    }

    // ── Tag Management ──────────────────────────────────────

    /** Add a tag. No-op if the tag already exists. */
    public void addTag(String tag) {
        BusinessRule.notNull(tag, "Tag");
        String trimmed = tag.trim();
        BusinessRule.ensure(!trimmed.isEmpty(), "Tag must not be blank");
        if (!this.tags.contains(trimmed)) {
            this.tags.add(trimmed);
            this.updatedAt = Instant.now();
        }
    }

    /** Remove a tag. No-op if the tag is not present. */
    public void removeTag(String tag) {
        if (tag != null && this.tags.remove(tag.trim())) {
            this.updatedAt = Instant.now();
        }
    }

    // ── Custom Fields ──────────────────────────────────────

    /** Set a custom field. Pass {@code null} as value to remove. */
    public void setCustomField(String key, Object value) {
        BusinessRule.notNull(key, "Custom field key");
        BusinessRule.ensure(!key.isBlank(), "Custom field key must not be blank");
        if (value == null) {
            this.customFields.remove(key);
        } else {
            this.customFields.put(key, value);
        }
        this.updatedAt = Instant.now();
    }

    // ── Verification ──────────────────────────────────────

    /**
     * Update the verification score.
     *
     * @param score an integer between 0 and 100 inclusive
     */
    public void updateVerificationScore(int score) {
        BusinessRule.ensure(
                score >= MIN_VERIFICATION_SCORE && score <= MAX_VERIFICATION_SCORE,
                "Verification score must be between %d and %d",
                MIN_VERIFICATION_SCORE, MAX_VERIFICATION_SCORE);
        this.verificationScore = score;
        this.updatedAt = Instant.now();
    }

    /** Whether this contact is considered "verified" (score &gt; 70). */
    public boolean isVerified() {
        return verificationScore > VERIFIED_THRESHOLD;
    }

    // ── Convenience Queries ─────────────────────────────────

    /** Whether this contact has a LinkedIn URL. */
    public boolean hasLinkedIn() {
        return linkedinUrl != null && !linkedinUrl.isBlank();
    }

    /** Whether this contact has a phone number. */
    public boolean hasPhone() {
        return phone != null && !phone.isBlank();
    }

    // ── Getters ─────────────────────────────────────────────

    public UUID getUserId() { return userId; }
    public UUID getPrimaryDumpId() { return primaryDumpId; }
    public UUID getCompanyId() { return companyId; }
    public String getName() { return name; }
    public Email getEmail() { return email; }
    public NormalizedEmail getNormalizedEmail() { return normalizedEmail; }
    public String getPhone() { return phone; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public String getSecondaryEmail() { return secondaryEmail; }
    public String getTitle() { return title; }
    public String getDepartment() { return department; }
    public SeniorityLevel getSeniorityLevel() { return seniorityLevel; }
    public String getLocation() { return location; }
    public String getTimezone() { return timezone; }
    public String getLanguage() { return language; }
    public String getDomain() { return domain; }
    public int getVerificationScore() { return verificationScore; }
    public LocalDate getLastActivityDate() { return lastActivityDate; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSource() { return source; }
    public String getNotes() { return notes; }
    public ContactStatus getStatus() { return status; }

    /** Returns an unmodifiable view of tags. */
    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    /** Returns an unmodifiable view of custom fields. */
    public Map<String, Object> getCustomFields() {
        return Collections.unmodifiableMap(customFields);
    }

    /** Returns an unmodifiable view of AI enrichment data. */
    public Map<String, Object> getAiEnrichment() {
        return Collections.unmodifiableMap(aiEnrichment);
    }

    // ── Builder (internal) ──────────────────────────────────

    /**
     * Internal builder used by {@link #create} and {@link #updateFields}.
     * Not public — this is an implementation detail of the aggregate.
     */
    static final class ContactBuilder {

        private UUID primaryDumpId;
        private UUID companyId;
        private String phone;
        private String linkedinUrl;
        private String secondaryEmail;
        private String title;
        private String department;
        private SeniorityLevel seniorityLevel;
        private String location;
        private String timezone;
        private String language;
        private Integer verificationScore;
        private LocalDate lastActivityDate;
        private String sourceUrl;
        private String source;
        private String notes;
        private List<String> tags;
        private Map<String, Object> customFields;
        private Map<String, Object> aiEnrichment;

        ContactBuilder() {}

        ContactBuilder primaryDumpId(UUID v) { this.primaryDumpId = v; return this; }
        ContactBuilder companyId(UUID v) { this.companyId = v; return this; }
        ContactBuilder phone(String v) { this.phone = v; return this; }
        ContactBuilder linkedinUrl(String v) { this.linkedinUrl = v; return this; }
        ContactBuilder secondaryEmail(String v) { this.secondaryEmail = v; return this; }
        ContactBuilder title(String v) { this.title = v; return this; }
        ContactBuilder department(String v) { this.department = v; return this; }
        ContactBuilder seniorityLevel(SeniorityLevel v) { this.seniorityLevel = v; return this; }
        ContactBuilder location(String v) { this.location = v; return this; }
        ContactBuilder timezone(String v) { this.timezone = v; return this; }
        ContactBuilder language(String v) { this.language = v; return this; }
        ContactBuilder verificationScore(int v) { this.verificationScore = v; return this; }
        ContactBuilder lastActivityDate(LocalDate v) { this.lastActivityDate = v; return this; }
        ContactBuilder sourceUrl(String v) { this.sourceUrl = v; return this; }
        ContactBuilder source(String v) { this.source = v; return this; }
        ContactBuilder notes(String v) { this.notes = v; return this; }
        ContactBuilder tags(List<String> v) { this.tags = v; return this; }
        ContactBuilder customFields(Map<String, Object> v) { this.customFields = v; return this; }
        ContactBuilder aiEnrichment(Map<String, Object> v) { this.aiEnrichment = v; return this; }

        void applyTo(Contact contact) {
            if (primaryDumpId != null) contact.primaryDumpId = primaryDumpId;
            if (companyId != null) contact.companyId = companyId;
            if (phone != null) contact.phone = phone;
            if (linkedinUrl != null) contact.linkedinUrl = linkedinUrl;
            if (secondaryEmail != null) contact.secondaryEmail = secondaryEmail;
            if (title != null) contact.title = title;
            if (department != null) contact.department = department;
            if (seniorityLevel != null) contact.seniorityLevel = seniorityLevel;
            if (location != null) contact.location = location;
            if (timezone != null) contact.timezone = timezone;
            if (language != null) contact.language = language;
            if (verificationScore != null) contact.verificationScore = verificationScore;
            if (lastActivityDate != null) contact.lastActivityDate = lastActivityDate;
            if (sourceUrl != null) contact.sourceUrl = sourceUrl;
            if (source != null) contact.source = source;
            if (notes != null) contact.notes = notes;
            if (tags != null) {
                contact.tags = new ArrayList<>(tags);
            }
            if (customFields != null) {
                contact.customFields = new HashMap<>(customFields);
            }
            if (aiEnrichment != null) {
                contact.aiEnrichment = new HashMap<>(aiEnrichment);
            }
        }
    }

    // ── Domain Events ──────────────────────────────────────

    private static final class ContactCreatedEvent extends DomainEvent {
        private final UUID contactId;
        private final UUID userId;
        private final String email;

        ContactCreatedEvent(UUID contactId, UUID userId, String email) {
            this.contactId = contactId;
            this.userId = userId;
            this.email = email;
        }

        public UUID getContactId() { return contactId; }
        public UUID getUserId() { return userId; }
        public String getEmail() { return email; }
    }

    private static final class ContactUpdatedEvent extends DomainEvent {
        private final UUID contactId;
        private final UUID userId;

        ContactUpdatedEvent(UUID contactId, UUID userId) {
            this.contactId = contactId;
            this.userId = userId;
        }

        public UUID getContactId() { return contactId; }
        public UUID getUserId() { return userId; }
    }

    private static final class ContactArchivedEvent extends DomainEvent {
        private final UUID contactId;
        private final UUID userId;

        ContactArchivedEvent(UUID contactId, UUID userId) {
            this.contactId = contactId;
            this.userId = userId;
        }

        public UUID getContactId() { return contactId; }
        public UUID getUserId() { return userId; }
    }

    private static final class ContactSoftDeletedEvent extends DomainEvent {
        private final UUID contactId;
        private final UUID userId;

        ContactSoftDeletedEvent(UUID contactId, UUID userId) {
            this.contactId = contactId;
            this.userId = userId;
        }

        public UUID getContactId() { return contactId; }
        public UUID getUserId() { return userId; }
    }

    private static final class ContactRestoredEvent extends DomainEvent {
        private final UUID contactId;
        private final UUID userId;

        ContactRestoredEvent(UUID contactId, UUID userId) {
            this.contactId = contactId;
            this.userId = userId;
        }

        public UUID getContactId() { return contactId; }
        public UUID getUserId() { return userId; }
    }
}
