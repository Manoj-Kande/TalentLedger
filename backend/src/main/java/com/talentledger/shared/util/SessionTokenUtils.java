package com.talentledger.shared.util;

import com.talentledger.shared.constants.SecurityConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates and hashes opaque session tokens.
 *
 * <p>Per ADR-003/ADR-033: sessions are our own opaque, high-entropy tokens
 * (never Clerk JWTs, never derived from a database primary key). Only the
 * SHA-256 hash of the token is ever persisted — the raw token is returned
 * to the client exactly once, at issuance, and must be presented on every
 * subsequent request via the {@code X-Session-Token} header.
 */
public final class SessionTokenUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SessionTokenUtils() {
    }

    /**
     * Generate a new cryptographically random opaque session token.
     *
     * @return a URL-safe, base64-encoded token with
     *         {@link SecurityConstants#SESSION_TOKEN_BYTES} bytes of entropy
     */
    public static String generateToken() {
        byte[] bytes = new byte[SecurityConstants.SESSION_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hash a raw token with SHA-256 for storage/lookup.
     *
     * <p>Never persist or compare raw tokens directly — only hashes.
     *
     * @param rawToken the raw token as presented by the client
     * @return the lowercase hex-encoded SHA-256 digest
     */
    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
