package com.talentledger.domain.user;

import com.talentledger.domain.shared.AggregateRoot;
import com.talentledger.domain.shared.BusinessRule;
import com.talentledger.domain.shared.DomainEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * User aggregate root — the central entity of the User bounded context.
 *
 * <p>Represents a registered user of TalentLedger. Extends
 * {@link AggregateRoot} to inherit identity, domain-event collection, and
 * soft-delete tracking.
 *
 * <p>Pure Java — zero framework annotations.
 */
public final class User extends AggregateRoot<UUID> {

    // ── Constants ─────────────────────────────────────────────

    private static final int MAX_EMAIL_LENGTH = 255;

    // ── Fields ───────────────────────────────────────────────

    private String clerkId;
    private String email;
    private boolean emailVerified;

    /**
     * Guest accounts (item #1, guest upload preview flow): created silently
     * on a visitor's first unauthenticated upload, reuse the entire normal
     * authenticated pipeline (auth, quota, dumps, contacts) rather than a
     * parallel non-persisting code path. Expire on their own — see
     * ScheduledJobs.purgeExpiredGuestAccounts — and are permanently claimed
     * (reassigned to a real account) via AuthService.claimGuestData when the
     * visitor signs up/in and chooses to keep their data.
     */
    private boolean isGuest;
    private Instant guestExpiresAt;
    private String name;
    private String avatarUrl;
    private UserRole role;
    private UserPlan plan;
    private UserStatus status;
    private String passwordHash;
    private boolean onboardingCompleted;
    private Map<String, Object> onboardingProfile;
    private int failedLoginAttempts;
    private Instant accountLockedUntil;
    private Instant lastLoginAt;
    private String lastLoginIp;
    private String lastLoginUserAgent;
    private boolean mfaEnabled;
    private String mfaType;
    private String mfaSecretEncrypted;
    private Instant mfaSetupCompletedAt;
    private int mfaBackupCodesRemaining;
    private String timezone;
    private String locale;
    private Map<String, Boolean> emailNotifications;
    private Instant acceptedTermsAt;
    private Instant acceptedPrivacyAt;
    private Instant deletionRequestedAt;
    private Instant dataExportRequestedAt;

    // ── Constructor (private) ────────────────────────────────

    private User(UUID id) {
        super(id);
        this.emailVerified = false;
        this.role = UserRole.USER;
        this.plan = UserPlan.FREE;
        this.status = UserStatus.ACTIVE;
        this.onboardingCompleted = false;
        this.onboardingProfile = new HashMap<>();
        this.failedLoginAttempts = 0;
        this.mfaEnabled = false;
        this.mfaBackupCodesRemaining = 0;
        this.timezone = "UTC";
        this.locale = "en-US";
        this.emailNotifications = new HashMap<>();
    }

    // ── Factory ───────────────────────────────────────

    /**
     * Create a new User aggregate.
     *
     * @param email the user's email address (must not be null or blank, max 255 chars)
     * @return a fully initialised User
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException
     *     on any rule violation
     */
    public static User create(String email) {
        return create(email, builder -> {});
    }

    /**
     * Create a new User aggregate with optional fields configured via the
     * provided builder consumer.
     *
     * @param email  the user's email address (must not be null or blank, max 255 chars)
     * @param config additional field configuration (may be empty)
     * @return a fully initialised User
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException
     *     on any rule violation
     */
    public static User create(String email, java.util.function.Consumer<UserBuilder> config) {
        BusinessRule.notNull(email, "Email");

        UUID userId = UUID.randomUUID();
        User user = new User(userId);

        user.email = email;

        // Apply optional configuration
        UserBuilder builder = new UserBuilder();
        config.accept(builder);
        builder.applyTo(user);

        // Validate invariants after all fields are set
        user.validateInvariants();

        Instant now = Instant.now();
        user.createdAt = now;
        user.updatedAt = now;

        user.registerEvent(new UserCreatedEvent(userId, email));

        return user;
    }

    // ── Invariant Validation ─────────────────────────────

    private void validateInvariants() {
        BusinessRule.notNull(email, "Email");
        BusinessRule.ensure(!email.isBlank(), "Email must not be blank");
        BusinessRule.ensure(email.length() <= MAX_EMAIL_LENGTH,
                "Email must not exceed %d characters", MAX_EMAIL_LENGTH);

        if (name != null) {
            BusinessRule.ensure(!name.isBlank(), "Name must not be blank when set");
        }

        // Status/sync check: if deletedAt IS NOT NULL then status must be PENDING_DELETION
        if (deletedAt != null) {
            BusinessRule.ensure(status == UserStatus.PENDING_DELETION,
                    "When deletedAt is set, status must be PENDING_DELETION");
        }
    }

    // ── Login ─────────────────────────────────────────

    /**
     * Record a successful login: reset failed attempts, update timestamps and IP/UA.
     *
     * @param ip       the client IP address (may be null)
     * @param userAgent the client user-agent string (may be null)
     */
    public void recordSuccessfulLogin(String ip, String userAgent) {
        this.failedLoginAttempts = 0;
        this.accountLockedUntil = null;
        this.lastLoginAt = Instant.now();
        this.lastLoginIp = ip;
        this.lastLoginUserAgent = userAgent;
        this.updatedAt = Instant.now();
    }

    /**
     * Record a failed login attempt.
     *
     * @return {@code true} if the account should now be locked or suspended
     *         based on the number of consecutive failed attempts
     */
    public boolean recordFailedLogin() {
        this.failedLoginAttempts++;
        this.updatedAt = Instant.now();

        // 20 failed attempts → suspend
        if (this.failedLoginAttempts >= 20) {
            this.status = UserStatus.SUSPENDED;
            registerEvent(new UserSuspendedEvent(this.id, "Too many failed login attempts"));
            return true;
        }

        // 10 failed attempts → lock 60 min
        if (this.failedLoginAttempts >= 10) {
            lockAccount(Duration.ofMinutes(60));
            return true;
        }

        // 5 failed attempts → lock 15 min
        if (this.failedLoginAttempts >= 5) {
            lockAccount(Duration.ofMinutes(15));
            return true;
        }

        return false;
    }

    /**
     * Lock the account for the given duration from now.
     *
     * @param duration the lock duration (must not be null, must be positive)
     */
    public void lockAccount(Duration duration) {
        BusinessRule.notNull(duration, "Lock duration");
        BusinessRule.ensure(!duration.isNegative() && !duration.isZero(),
                "Lock duration must be positive");
        this.accountLockedUntil = Instant.now().plus(duration);
        this.updatedAt = Instant.now();
        registerEvent(new UserAccountLockedEvent(this.id, duration));
    }

    /** Unlock the account immediately. */
    public void unlockAccount() {
        this.accountLockedUntil = null;
        this.failedLoginAttempts = 0;
        this.updatedAt = Instant.now();
    }

    /**
     * Check whether the account is currently locked.
     *
     * @return {@code true} if accountLockedUntil is set and in the future
     */
    public boolean isAccountLocked() {
        return accountLockedUntil != null && Instant.now().isBefore(accountLockedUntil);
    }

    // ── MFA ───────────────────────────────────────────

    /**
     * Enable MFA with the given type and encrypted secret.
     *
     * @param type            the MFA type (e.g. "TOTP", "SMS", "EMAIL")
     * @param secretEncrypted the encrypted MFA secret
     */
    public void enableMfa(String type, String secretEncrypted) {
        BusinessRule.notNull(type, "MFA type");
        BusinessRule.ensure(!type.isBlank(), "MFA type must not be blank");
        BusinessRule.notNull(secretEncrypted, "MFA secret");
        BusinessRule.ensure(!secretEncrypted.isBlank(), "MFA secret must not be blank");

        this.mfaEnabled = true;
        this.mfaType = type;
        this.mfaSecretEncrypted = secretEncrypted;
        this.mfaSetupCompletedAt = null;
        this.mfaBackupCodesRemaining = 0;
        this.updatedAt = Instant.now();
    }

    /**
     * Complete MFA setup after the user has verified their first code.
     *
     * @param backupCodesCount the number of backup codes generated
     */
    public void completeMfaSetup(int backupCodesCount) {
        BusinessRule.ensure(mfaEnabled, "MFA must be enabled before completing setup");
        BusinessRule.ensure(backupCodesCount >= 0, "Backup codes count must be non-negative");
        this.mfaSetupCompletedAt = Instant.now();
        this.mfaBackupCodesRemaining = backupCodesCount;
        this.updatedAt = Instant.now();
        registerEvent(new MfaEnabledEvent(this.id, this.mfaType));
    }

    /** Disable MFA on this account. */
    public void disableMfa() {
        this.mfaEnabled = false;
        this.mfaType = null;
        this.mfaSecretEncrypted = null;
        this.mfaSetupCompletedAt = null;
        this.mfaBackupCodesRemaining = 0;
        this.updatedAt = Instant.now();
        registerEvent(new MfaDisabledEvent(this.id));
    }

    /** Record that one backup code was just consumed at login. */
    public void consumeBackupCode() {
        BusinessRule.ensure(mfaBackupCodesRemaining > 0, "No backup codes remaining");
        this.mfaBackupCodesRemaining = Math.max(0, this.mfaBackupCodesRemaining - 1);
        this.updatedAt = Instant.now();
    }

    /** Replace all backup codes with a fresh batch (old ones are invalidated by the caller deleting their rows). */
    public void regenerateBackupCodes(int newCount) {
        BusinessRule.ensure(mfaEnabled, "MFA must be enabled to regenerate backup codes");
        BusinessRule.ensure(newCount >= 0, "Backup codes count must be non-negative");
        this.mfaBackupCodesRemaining = newCount;
        this.updatedAt = Instant.now();
    }

    // ── Role / Plan Queries ────────────────────────────

    /** @return true if the user has PREMIUM or ADMIN role */
    public boolean isPremium() {
        return role == UserRole.PREMIUM || role == UserRole.ADMIN;
    }

    /** @return true if the user has ADMIN role */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    // ── Email ──────────────────────────────────────

    /**
     * Update the user's email address.
     *
     * @param newEmail the new email address (must not be null or blank, max 255 chars)
     */
    public void updateEmail(String newEmail) {
        BusinessRule.notNull(newEmail, "New email");
        BusinessRule.ensure(!newEmail.isBlank(), "New email must not be blank");
        BusinessRule.ensure(newEmail.length() <= MAX_EMAIL_LENGTH,
                "Email must not exceed %d characters", MAX_EMAIL_LENGTH);

        String oldEmail = this.email;
        this.email = newEmail;
        this.emailVerified = false;
        this.updatedAt = Instant.now();
        registerEvent(new UserEmailChangedEvent(this.id, oldEmail, newEmail));
    }

    // ── Deletion / Export ──────────────────────────────

    /** Request account deletion. Sets status to PENDING_DELETION. */
    public void requestDeletion() {
        this.status = UserStatus.PENDING_DELETION;
        this.deletionRequestedAt = Instant.now();
        this.updatedAt = Instant.now();
        registerEvent(new UserDeletionRequestedEvent(this.id, this.email));
    }

    /**
     * Ban this account for the given reason. Banned users cannot log in
     * (enforced by {@code UserSyncFilter} / login checks against status).
     *
     * @param reason required, non-blank justification for the audit trail
     */
    public void ban(String reason) {
        BusinessRule.ensure(reason != null && !reason.isBlank(), "Ban reason must not be blank");
        this.status = UserStatus.BANNED;
        this.updatedAt = Instant.now();
    }

    /** Restore a banned account to ACTIVE. */
    public void unban() {
        BusinessRule.ensure(this.status == UserStatus.BANNED, "User is not currently banned");
        this.status = UserStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * Replace this user's password hash (already bcrypt-encoded by the caller —
     * this method never hashes anything itself, matching {@link Credentials}'s
     * naming convention that "hash" fields hold what they say they hold).
     *
     * @param newPasswordHash a non-blank bcrypt hash
     */
    public void changePassword(String newPasswordHash) {
        BusinessRule.ensure(newPasswordHash != null && !newPasswordHash.isBlank(), "Password hash must not be blank");
        this.passwordHash = newPasswordHash;
        this.updatedAt = Instant.now();
    }

    /** Link this user to a Clerk account id (e.g. first Google sign-in for an existing native-auth user). */
    public void linkClerkId(String clerkId) {
        BusinessRule.ensure(clerkId != null && !clerkId.isBlank(), "Clerk id must not be blank");
        this.clerkId = clerkId;
        this.updatedAt = Instant.now();
    }

    /** Mark this user's email as verified (called after a successful token check). */
    public void markEmailVerified() {
        this.emailVerified = true;
        this.updatedAt = Instant.now();
    }

    /** Request a data export. */
    public void requestExport() {
        this.dataExportRequestedAt = Instant.now();
        this.updatedAt = Instant.now();
        registerEvent(new UserDataExportRequestedEvent(this.id, this.email));
    }

    // ── Plan ─────────────────────────────────────────

    /**
     * Change the user's subscription plan.
     *
     * @param newPlan the new plan (must not be null)
     */
    public void changePlan(UserPlan newPlan) {
        BusinessRule.notNull(newPlan, "New plan");
        UserPlan oldPlan = this.plan;
        this.plan = newPlan;
        this.updatedAt = Instant.now();
        if (oldPlan != newPlan) {
            registerEvent(new UserPlanChangedEvent(this.id, oldPlan, newPlan));
        }
    }

    // ── Role ─────────────────────────────────────────

    /**
     * Change the user's role (USER / PREMIUM / ADMIN). Distinct from
     * {@link #changePlan}: role governs permissions (e.g. admin panel
     * access), plan governs tier/quota limits. An admin granting "Premium"
     * without a payment provider (per the product's admin-managed-access
     * model) should typically change both together — that pairing is the
     * caller's responsibility, not enforced here, since role and plan are
     * independent axes.
     *
     * @param newRole the new role (must not be null)
     */
    public void changeRole(UserRole newRole) {
        BusinessRule.notNull(newRole, "New role");
        UserRole oldRole = this.role;
        this.role = newRole;
        this.updatedAt = Instant.now();
        if (oldRole != newRole) {
            registerEvent(new UserRoleChangedEvent(this.id, oldRole, newRole));
        }
    }

    // ── Getters ────────────────────────────────────

    public String getClerkId() { return clerkId; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getName() { return name; }
    public String getAvatarUrl() { return avatarUrl; }
    public UserRole getRole() { return role; }
    public UserPlan getPlan() { return plan; }
    public boolean isGuest() { return isGuest; }
    public Instant getGuestExpiresAt() { return guestExpiresAt; }
    public UserStatus getStatus() { return status; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public Map<String, Object> getOnboardingProfile() {
        return Collections.unmodifiableMap(onboardingProfile);
    }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getAccountLockedUntil() { return accountLockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public String getLastLoginIp() { return lastLoginIp; }
    public String getLastLoginUserAgent() { return lastLoginUserAgent; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public String getMfaType() { return mfaType; }
    public String getMfaSecretEncrypted() { return mfaSecretEncrypted; }
    public Instant getMfaSetupCompletedAt() { return mfaSetupCompletedAt; }
    public int getMfaBackupCodesRemaining() { return mfaBackupCodesRemaining; }
    public String getTimezone() { return timezone; }
    public String getLocale() { return locale; }
    public Map<String, Boolean> getEmailNotifications() {
        return Collections.unmodifiableMap(emailNotifications);
    }
    public Instant getAcceptedTermsAt() { return acceptedTermsAt; }
    public Instant getAcceptedPrivacyAt() { return acceptedPrivacyAt; }
    public Instant getDeletionRequestedAt() { return deletionRequestedAt; }
    public Instant getDataExportRequestedAt() { return dataExportRequestedAt; }

    // ── Setters (for reconstitution from DB) ──────────────────────

    void setClerkId(String clerkId) { this.clerkId = clerkId; }
    void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    void setName(String name) { this.name = name; }
    void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    void setRole(UserRole role) { this.role = role; }
    void setPlan(UserPlan plan) { this.plan = plan; }
    void setStatus(UserStatus status) { this.status = status; }
    void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    void setOnboardingCompleted(boolean onboardingCompleted) { this.onboardingCompleted = onboardingCompleted; }
    void setOnboardingProfile(Map<String, Object> onboardingProfile) {
        this.onboardingProfile = onboardingProfile != null ? new HashMap<>(onboardingProfile) : new HashMap<>();
    }
    void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
    void setAccountLockedUntil(Instant accountLockedUntil) { this.accountLockedUntil = accountLockedUntil; }
    void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    void setLastLoginIp(String lastLoginIp) { this.lastLoginIp = lastLoginIp; }
    void setLastLoginUserAgent(String lastLoginUserAgent) { this.lastLoginUserAgent = lastLoginUserAgent; }
    void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }
    void setMfaType(String mfaType) { this.mfaType = mfaType; }
    void setMfaSecretEncrypted(String mfaSecretEncrypted) { this.mfaSecretEncrypted = mfaSecretEncrypted; }
    void setMfaSetupCompletedAt(Instant mfaSetupCompletedAt) { this.mfaSetupCompletedAt = mfaSetupCompletedAt; }
    void setMfaBackupCodesRemaining(int mfaBackupCodesRemaining) { this.mfaBackupCodesRemaining = mfaBackupCodesRemaining; }
    void setTimezone(String timezone) { this.timezone = timezone; }
    void setLocale(String locale) { this.locale = locale; }
    void setEmailNotifications(Map<String, Boolean> emailNotifications) {
        this.emailNotifications = emailNotifications != null ? new HashMap<>(emailNotifications) : new HashMap<>();
    }
    void setAcceptedTermsAt(Instant acceptedTermsAt) { this.acceptedTermsAt = acceptedTermsAt; }
    void setAcceptedPrivacyAt(Instant acceptedPrivacyAt) { this.acceptedPrivacyAt = acceptedPrivacyAt; }
    void setDeletionRequestedAt(Instant deletionRequestedAt) { this.deletionRequestedAt = deletionRequestedAt; }
    void setDataExportRequestedAt(Instant dataExportRequestedAt) { this.dataExportRequestedAt = dataExportRequestedAt; }

    // ── Builder (internal) ──────────────────────────────

    public static final class UserBuilder {

        private String clerkId;
        private String name;
        private String avatarUrl;
        private UserRole role;
        private UserPlan plan;
        private String passwordHash;
        private Map<String, Object> onboardingProfile;
        private String timezone;
        private String locale;
        private Map<String, Boolean> emailNotifications;
        private Instant acceptedTermsAt;
        private Instant acceptedPrivacyAt;
        private Boolean isGuest;
        private Instant guestExpiresAt;

        UserBuilder() {}

        public UserBuilder clerkId(String v) { this.clerkId = v; return this; }
        public UserBuilder name(String v) { this.name = v; return this; }
        public UserBuilder avatarUrl(String v) { this.avatarUrl = v; return this; }
        public UserBuilder role(UserRole v) { this.role = v; return this; }
        public UserBuilder plan(UserPlan v) { this.plan = v; return this; }
        public UserBuilder passwordHash(String v) { this.passwordHash = v; return this; }
        public UserBuilder onboardingProfile(Map<String, Object> v) { this.onboardingProfile = v; return this; }
        public UserBuilder timezone(String v) { this.timezone = v; return this; }
        public UserBuilder locale(String v) { this.locale = v; return this; }
        public UserBuilder emailNotifications(Map<String, Boolean> v) { this.emailNotifications = v; return this; }
        public UserBuilder acceptedTermsAt(Instant v) { this.acceptedTermsAt = v; return this; }
        public UserBuilder acceptedPrivacyAt(Instant v) { this.acceptedPrivacyAt = v; return this; }
        public UserBuilder isGuest(boolean v) { this.isGuest = v; return this; }
        public UserBuilder guestExpiresAt(Instant v) { this.guestExpiresAt = v; return this; }

        void applyTo(User user) {
            if (clerkId != null) user.clerkId = clerkId;
            if (name != null) user.name = name;
            if (avatarUrl != null) user.avatarUrl = avatarUrl;
            if (role != null) user.role = role;
            if (plan != null) user.plan = plan;
            if (passwordHash != null) user.passwordHash = passwordHash;
            if (onboardingProfile != null) {
                user.onboardingProfile = new HashMap<>(onboardingProfile);
            }
            if (timezone != null) user.timezone = timezone;
            if (locale != null) user.locale = locale;
            if (emailNotifications != null) {
                user.emailNotifications = new HashMap<>(emailNotifications);
            }
            if (acceptedTermsAt != null) user.acceptedTermsAt = acceptedTermsAt;
            if (acceptedPrivacyAt != null) user.acceptedPrivacyAt = acceptedPrivacyAt;
            if (isGuest != null) user.isGuest = isGuest;
            if (guestExpiresAt != null) user.guestExpiresAt = guestExpiresAt;
        }
    }

    // ── Domain Events ───────────────────────────────────

    private static final class UserCreatedEvent extends DomainEvent {
        private final UUID userId;
        private final String email;
        UserCreatedEvent(UUID userId, String email) {
            this.userId = userId;
            this.email = email;
        }
        public UUID getUserId() { return userId; }
        public String getEmail() { return email; }
    }

    private static final class UserEmailChangedEvent extends DomainEvent {
        private final UUID userId;
        private final String oldEmail;
        private final String newEmail;
        UserEmailChangedEvent(UUID userId, String oldEmail, String newEmail) {
            this.userId = userId;
            this.oldEmail = oldEmail;
            this.newEmail = newEmail;
        }
        public UUID getUserId() { return userId; }
        public String getOldEmail() { return oldEmail; }
        public String getNewEmail() { return newEmail; }
    }

    private static final class UserAccountLockedEvent extends DomainEvent {
        private final UUID userId;
        private final Duration lockDuration;
        UserAccountLockedEvent(UUID userId, Duration lockDuration) {
            this.userId = userId;
            this.lockDuration = lockDuration;
        }
        public UUID getUserId() { return userId; }
        public Duration getLockDuration() { return lockDuration; }
    }

    private static final class UserSuspendedEvent extends DomainEvent {
        private final UUID userId;
        private final String reason;
        UserSuspendedEvent(UUID userId, String reason) {
            this.userId = userId;
            this.reason = reason;
        }
        public UUID getUserId() { return userId; }
        public String getReason() { return reason; }
    }

    private static final class UserDeletionRequestedEvent extends DomainEvent {
        private final UUID userId;
        private final String email;
        UserDeletionRequestedEvent(UUID userId, String email) {
            this.userId = userId;
            this.email = email;
        }
        public UUID getUserId() { return userId; }
        public String getEmail() { return email; }
    }

    private static final class UserDataExportRequestedEvent extends DomainEvent {
        private final UUID userId;
        private final String email;
        UserDataExportRequestedEvent(UUID userId, String email) {
            this.userId = userId;
            this.email = email;
        }
        public UUID getUserId() { return userId; }
        public String getEmail() { return email; }
    }

    private static final class UserPlanChangedEvent extends DomainEvent {
        private final UUID userId;
        private final UserPlan oldPlan;
        private final UserPlan newPlan;
        UserPlanChangedEvent(UUID userId, UserPlan oldPlan, UserPlan newPlan) {
            this.userId = userId;
            this.oldPlan = oldPlan;
            this.newPlan = newPlan;
        }
        public UUID getUserId() { return userId; }
        public UserPlan getOldPlan() { return oldPlan; }
        public UserPlan getNewPlan() { return newPlan; }
    }

    private static final class UserRoleChangedEvent extends DomainEvent {
        private final UUID userId;
        private final UserRole oldRole;
        private final UserRole newRole;
        UserRoleChangedEvent(UUID userId, UserRole oldRole, UserRole newRole) {
            this.userId = userId;
            this.oldRole = oldRole;
            this.newRole = newRole;
        }
        public UUID getUserId() { return userId; }
        public UserRole getOldRole() { return oldRole; }
        public UserRole getNewRole() { return newRole; }
    }

    private static final class MfaEnabledEvent extends DomainEvent {
        private final UUID userId;
        private final String mfaType;
        MfaEnabledEvent(UUID userId, String mfaType) {
            this.userId = userId;
            this.mfaType = mfaType;
        }
        public UUID getUserId() { return userId; }
        public String getMfaType() { return mfaType; }
    }

    private static final class MfaDisabledEvent extends DomainEvent {
        private final UUID userId;
        MfaDisabledEvent(UUID userId) {
            this.userId = userId;
        }
        public UUID getUserId() { return userId; }
    }
}
