package com.talentledger.domain.auth;

import com.talentledger.domain.shared.BusinessRule;

/**
 * Immutable value object holding a user's authentication credentials
 * <strong>as presented at login</strong> — i.e. the raw, plaintext password
 * the caller typed in, not a hash. It is compared against the stored bcrypt
 * hash via {@code PasswordEncoder.matches(raw, encoded)}; it must NEVER be
 * hashed again before that comparison.
 *
 * <p>(Earlier revisions of this class called this field "passwordHash",
 * which was incorrect and risked a future maintainer double-hashing it,
 * permanently breaking login. Renamed to {@code rawPassword} for clarity.)
 *
 * <p>Instances are created via static factory methods and never mutated.
 */
public final class Credentials {

    private final String email;
    private final String rawPassword;
    private final String mfaCode;

    private Credentials(String email, String rawPassword, String mfaCode) {
        this.email = email;
        this.rawPassword = rawPassword;
        this.mfaCode = mfaCode;
    }

    /**
     * Create credentials without MFA.
     *
     * @param email       non-blank email address
     * @param rawPassword non-blank plaintext password as typed by the user
     * @return immutable Credentials instance
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException if arguments are blank
     */
    public static Credentials of(String email, String rawPassword) {
        return of(email, rawPassword, null);
    }

    /**
     * Create credentials with optional MFA code.
     *
     * @param email       non-blank email address
     * @param rawPassword non-blank plaintext password as typed by the user
     * @param mfaCode     nullable TOTP code if MFA is enabled
     * @return immutable Credentials instance
     * @throws com.talentledger.domain.shared.BusinessRuleViolationException if email or rawPassword are blank
     */
    public static Credentials of(String email, String rawPassword, String mfaCode) {
        BusinessRule.ensure(email != null && !email.isBlank(), "Email must not be blank");
        BusinessRule.ensure(rawPassword != null && !rawPassword.isBlank(), "Password must not be blank");
        return new Credentials(email.trim(), rawPassword, mfaCode);
    }

    // ── Getters ────────────────────────────────────────────

    public String getEmail() {
        return email;
    }

    /**
     * @return the raw plaintext password as typed at login — pass this
     *         directly as the "raw" argument to {@code PasswordEncoder.matches};
     *         never hash it yourself first.
     */
    public String getRawPassword() {
        return rawPassword;
    }

    /**
     * @return the TOTP code if MFA is enabled, otherwise null
     */
    public String getMfaCode() {
        return mfaCode;
    }

    /**
     * @return true if an MFA code is present
     */
    public boolean hasMfa() {
        return mfaCode != null && !mfaCode.isBlank();
    }
}
