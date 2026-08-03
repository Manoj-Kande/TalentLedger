package com.talentledger.domain.user;

import com.talentledger.domain.shared.BusinessRule;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable value object representing a user's resource quotas.
 *
 * <p>Tracks usage counters and limits for dumps, contacts, uploads,
 * AI credits, and storage. All mutation methods return a NEW instance.
 *
 * <p>Use the {@link Builder} to construct instances.
 * Pure Java — zero framework annotations.
 */
public final class UserQuota {

    // ── Constants ─────────────────────────────────────────────

    /** 5 MB in bytes. */
    private static final long DEFAULT_STORAGE_LIMIT = 5_242_880L;

    // ── Fields (all final — immutable) ────────────────────────

    private final UUID userId;
    private final int activeDumpsCount;
    private final int activeDumpsLimit;
    private final int contactsStoredCount;
    private final int contactsStoredLimit;
    private final int uploadsThisMonthCount;
    private final int uploadsMonthlyLimit;
    private final int aiCreditsUsed;
    private final int aiCreditsLimit;
    private final long storageBytesUsed;
    private final long storageBytesLimit;
    private final boolean hasActiveFreeDump;
    private final Instant lastResetAt;
    private final Instant updatedAt;

    // ── Private Constructor ───────────────────────────────────

    private UserQuota(Builder builder) {
        this.userId = builder.userId;
        this.activeDumpsCount = builder.activeDumpsCount;
        this.activeDumpsLimit = builder.activeDumpsLimit;
        this.contactsStoredCount = builder.contactsStoredCount;
        this.contactsStoredLimit = builder.contactsStoredLimit;
        this.uploadsThisMonthCount = builder.uploadsThisMonthCount;
        this.uploadsMonthlyLimit = builder.uploadsMonthlyLimit;
        this.aiCreditsUsed = builder.aiCreditsUsed;
        this.aiCreditsLimit = builder.aiCreditsLimit;
        this.storageBytesUsed = builder.storageBytesUsed;
        this.storageBytesLimit = builder.storageBytesLimit;
        this.hasActiveFreeDump = builder.hasActiveFreeDump;
        this.lastResetAt = builder.lastResetAt;
        this.updatedAt = builder.updatedAt;

        validateCounts();
    }

    // ── Validation ────────────────────────────────────────────

    private void validateCounts() {
        BusinessRule.ensure(activeDumpsCount >= 0, "activeDumpsCount must be >= 0");
        BusinessRule.ensure(activeDumpsLimit >= 0, "activeDumpsLimit must be >= 0");
        BusinessRule.ensure(contactsStoredCount >= 0, "contactsStoredCount must be >= 0");
        BusinessRule.ensure(contactsStoredLimit >= 0, "contactsStoredLimit must be >= 0");
        BusinessRule.ensure(uploadsThisMonthCount >= 0, "uploadsThisMonthCount must be >= 0");
        BusinessRule.ensure(uploadsMonthlyLimit >= 0, "uploadsMonthlyLimit must be >= 0");
        BusinessRule.ensure(aiCreditsUsed >= 0, "aiCreditsUsed must be >= 0");
        BusinessRule.ensure(aiCreditsLimit >= 0, "aiCreditsLimit must be >= 0");
        BusinessRule.ensure(storageBytesUsed >= 0, "storageBytesUsed must be >= 0");
        BusinessRule.ensure(storageBytesLimit >= 0, "storageBytesLimit must be >= 0");
    }

    // ── Quota Checks ──────────────────────────────────────────

    /** @return true if the user can create another dump */
    public boolean canCreateDump() {
        return activeDumpsCount < activeDumpsLimit;
    }

    /** @return true if the user can add {@code count} more contacts */
    public boolean canAddContacts(int count) {
        BusinessRule.ensure(count >= 0, "Contact count to add must be non-negative");
        return contactsStoredCount + count <= contactsStoredLimit;
    }

    /** @return true if the user can upload another file this month */
    public boolean canUpload() {
        return uploadsThisMonthCount < uploadsMonthlyLimit;
    }

    /** @return true if the user has enough AI credits for the requested amount */
    public boolean canUseAi(int creditsNeeded) {
        BusinessRule.ensure(creditsNeeded >= 0, "AI credits needed must be non-negative");
        return aiCreditsUsed + creditsNeeded <= aiCreditsLimit;
    }

    /** @return true if the user can store {@code size} more bytes */
    public boolean canStoreBytes(long size) {
        BusinessRule.ensure(size >= 0, "Storage size must be non-negative");
        return storageBytesUsed + size <= storageBytesLimit;
    }

    /**
     * Check whether the user has free-dump quota available.
     * Only FREE-plan users who have not yet used their single free dump qualify.
     *
     * <p>Note: this method does NOT have direct access to the user's plan.
     * The calling code should additionally check that the user's plan is FREE.
     * This method only checks the {@code hasActiveFreeDump} flag.
     *
     * @return true if no active free dump has been used
     */
    public boolean hasFreeDumpQuota() {
        return !hasActiveFreeDump;
    }

    // ── Immutable Mutation Methods ────────────────────────────

    /**
     * Return a new UserQuota with uploadsThisMonthCount incremented by 1.
     *
     * @return a new immutable instance
     */
    public UserQuota incrementUploads() {
        return new Builder(this)
                .uploadsThisMonthCount(this.uploadsThisMonthCount + 1)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Return a new UserQuota with monthly counters reset to zero.
     * Resets: uploadsThisMonthCount.
     *
     * @return a new immutable instance
     */
    public UserQuota resetMonthly() {
        return new Builder(this)
                .uploadsThisMonthCount(0)
                .lastResetAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /** Return a new UserQuota reflecting one more active dump (and, for FREE users, the free dump slot consumed). */
    public UserQuota addDump() {
        return new Builder(this)
                .activeDumpsCount(this.activeDumpsCount + 1)
                .hasActiveFreeDump(true)
                .updatedAt(Instant.now())
                .build();
    }

    /** Return a new UserQuota with contactsStoredCount increased by {@code count}. */
    public UserQuota addContacts(int count) {
        BusinessRule.ensure(count >= 0, "Contact count to add must be non-negative");
        return new Builder(this)
                .contactsStoredCount(this.contactsStoredCount + count)
                .updatedAt(Instant.now())
                .build();
    }

    /** Return a new UserQuota with storageBytesUsed increased by {@code size}. */
    public UserQuota addStorageBytes(long size) {
        BusinessRule.ensure(size >= 0, "Storage size to add must be non-negative");
        return new Builder(this)
                .storageBytesUsed(this.storageBytesUsed + size)
                .updatedAt(Instant.now())
                .build();
    }

    /** Return a new UserQuota with contactsStoredCount decreased by {@code count} (floored at 0). */
    public UserQuota removeContacts(int count) {
        BusinessRule.ensure(count >= 0, "Contact count to remove must be non-negative");
        return new Builder(this)
                .contactsStoredCount(Math.max(0, this.contactsStoredCount - count))
                .updatedAt(Instant.now())
                .build();
    }

    /** Return a new UserQuota with aiCreditsUsed increased by {@code count}. */
    public UserQuota useAiCredits(int count) {
        BusinessRule.ensure(count >= 0, "AI credits to use must be non-negative");
        return new Builder(this)
                .aiCreditsUsed(this.aiCreditsUsed + count)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Return a new UserQuota with limits updated to match {@code newPlan}, while
     * preserving all current usage counts (activeDumpsCount, contactsStoredCount,
     * etc. are untouched — only the *limits* move). Used when a Stripe
     * subscription webhook confirms a plan change; usage counts shouldn't reset
     * just because the plan changed mid-cycle.
     */
    public UserQuota changePlan(UserPlan newPlan) {
        Builder b = new Builder(this);
        Builder.applyPlanDefaults(b, newPlan);
        b.updatedAt = Instant.now();
        return b.build();
    }

    // ── Getters ───────────────────────────────────────────────

    public UUID getUserId() { return userId; }
    public int getActiveDumpsCount() { return activeDumpsCount; }
    public int getActiveDumpsLimit() { return activeDumpsLimit; }
    public int getContactsStoredCount() { return contactsStoredCount; }
    public int getContactsStoredLimit() { return contactsStoredLimit; }
    public int getUploadsThisMonthCount() { return uploadsThisMonthCount; }
    public int getUploadsMonthlyLimit() { return uploadsMonthlyLimit; }
    public int getAiCreditsUsed() { return aiCreditsUsed; }
    public int getAiCreditsLimit() { return aiCreditsLimit; }
    public long getStorageBytesUsed() { return storageBytesUsed; }
    public long getStorageBytesLimit() { return storageBytesLimit; }
    public boolean isHasActiveFreeDump() { return hasActiveFreeDump; }
    public Instant getLastResetAt() { return lastResetAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ── Builder ───────────────────────────────────────────────

    /**
     * Builder for constructing {@link UserQuota} instances.
     *
     * <p>All counts default to 0. Limits default to plan-specific values
     * when using the {@link #forUser(UUID, UserPlan)} factory.
     */
    public static final class Builder {

        private UUID userId;
        private int activeDumpsCount;
        private int activeDumpsLimit = 1;
        private int contactsStoredCount;
        private int contactsStoredLimit = 200;
        private int uploadsThisMonthCount;
        private int uploadsMonthlyLimit = 5;
        private int aiCreditsUsed;
        private int aiCreditsLimit;
        private long storageBytesUsed;
        private long storageBytesLimit = DEFAULT_STORAGE_LIMIT;
        private boolean hasActiveFreeDump;
        private Instant lastResetAt;
        private Instant updatedAt;

        /** Create a builder with defaults for a given user and plan. */
        public static Builder forUser(UUID userId, UserPlan plan) {
            Builder b = new Builder();
            b.userId = userId;
            applyPlanDefaults(b, plan);
            b.updatedAt = Instant.now();
            b.lastResetAt = Instant.now();
            return b;
        }

        /** Copy constructor — starts from an existing UserQuota. */
        public Builder(UserQuota existing) {
            this.userId = existing.userId;
            this.activeDumpsCount = existing.activeDumpsCount;
            this.activeDumpsLimit = existing.activeDumpsLimit;
            this.contactsStoredCount = existing.contactsStoredCount;
            this.contactsStoredLimit = existing.contactsStoredLimit;
            this.uploadsThisMonthCount = existing.uploadsThisMonthCount;
            this.uploadsMonthlyLimit = existing.uploadsMonthlyLimit;
            this.aiCreditsUsed = existing.aiCreditsUsed;
            this.aiCreditsLimit = existing.aiCreditsLimit;
            this.storageBytesUsed = existing.storageBytesUsed;
            this.storageBytesLimit = existing.storageBytesLimit;
            this.hasActiveFreeDump = existing.hasActiveFreeDump;
            this.lastResetAt = existing.lastResetAt;
            this.updatedAt = existing.updatedAt;
        }

        public Builder() {}

        public Builder userId(UUID v) { this.userId = v; return this; }
        public Builder activeDumpsCount(int v) { this.activeDumpsCount = v; return this; }
        public Builder activeDumpsLimit(int v) { this.activeDumpsLimit = v; return this; }
        public Builder contactsStoredCount(int v) { this.contactsStoredCount = v; return this; }
        public Builder contactsStoredLimit(int v) { this.contactsStoredLimit = v; return this; }
        public Builder uploadsThisMonthCount(int v) { this.uploadsThisMonthCount = v; return this; }
        public Builder uploadsMonthlyLimit(int v) { this.uploadsMonthlyLimit = v; return this; }
        public Builder aiCreditsUsed(int v) { this.aiCreditsUsed = v; return this; }
        public Builder aiCreditsLimit(int v) { this.aiCreditsLimit = v; return this; }
        public Builder storageBytesUsed(long v) { this.storageBytesUsed = v; return this; }
        public Builder storageBytesLimit(long v) { this.storageBytesLimit = v; return this; }
        public Builder hasActiveFreeDump(boolean v) { this.hasActiveFreeDump = v; return this; }
        public Builder lastResetAt(Instant v) { this.lastResetAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public UserQuota build() {
            return new UserQuota(this);
        }

        private static void applyPlanDefaults(Builder b, UserPlan plan) {
            switch (plan) {
                case FREE:
                    b.activeDumpsLimit = com.talentledger.shared.constants.QuotaConstants.FREE_ACTIVE_DUMPS;
                    b.contactsStoredLimit = com.talentledger.shared.constants.QuotaConstants.FREE_CONTACTS;
                    b.uploadsMonthlyLimit = com.talentledger.shared.constants.QuotaConstants.FREE_UPLOADS_MONTHLY;
                    b.aiCreditsLimit = com.talentledger.shared.constants.QuotaConstants.FREE_AI_CREDITS;
                    b.storageBytesLimit = com.talentledger.shared.constants.QuotaConstants.FREE_STORAGE_BYTES;
                    break;
                case PRO:
                    b.activeDumpsLimit = com.talentledger.shared.constants.QuotaConstants.PRO_ACTIVE_DUMPS;
                    b.contactsStoredLimit = com.talentledger.shared.constants.QuotaConstants.PRO_CONTACTS;
                    b.uploadsMonthlyLimit = com.talentledger.shared.constants.QuotaConstants.PRO_UPLOADS_MONTHLY;
                    b.aiCreditsLimit = com.talentledger.shared.constants.QuotaConstants.PRO_AI_CREDITS;
                    b.storageBytesLimit = com.talentledger.shared.constants.QuotaConstants.PRO_STORAGE_BYTES;
                    break;
                case TEAM:
                    b.activeDumpsLimit = 50;
                    b.contactsStoredLimit = com.talentledger.shared.constants.QuotaConstants.TEAM_CONTACTS;
                    b.uploadsMonthlyLimit = com.talentledger.shared.constants.QuotaConstants.TEAM_UPLOADS_MONTHLY;
                    b.aiCreditsLimit = com.talentledger.shared.constants.QuotaConstants.TEAM_AI_CREDITS;
                    b.storageBytesLimit = com.talentledger.shared.constants.QuotaConstants.TEAM_FILE_SIZE_BYTES * 50L; // 5 GB team pool
                    break;
                case ENTERPRISE:
                    b.activeDumpsLimit = Integer.MAX_VALUE;
                    b.contactsStoredLimit = Integer.MAX_VALUE;
                    b.uploadsMonthlyLimit = Integer.MAX_VALUE;
                    b.aiCreditsLimit = Integer.MAX_VALUE;
                    b.storageBytesLimit = Long.MAX_VALUE;
                    break;
                default:
                    break;
            }
        }
    }
}
