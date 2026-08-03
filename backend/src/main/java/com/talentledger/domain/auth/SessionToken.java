package com.talentledger.domain.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Immutable value object representing an opaque session token.
 *
 * <p>Contains both the raw hex token (returned to the client) and its
 * SHA-256 hash (stored in the database). Tokens are generated using
 * {@link SecureRandom} for cryptographic security.
 *
 * <p>This is NOT a Clerk JWT — it is an opaque bearer token managed
 * entirely by TalentLedger.
 */
public final class SessionToken {

    private static final String HEX_CHARS = "0123456789abcdef";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String rawToken;
    private final String tokenHash;

    private SessionToken(String rawToken, String tokenHash) {
        this.rawToken = rawToken;
        this.tokenHash = tokenHash;
    }

    /**
     * Generate a new cryptographically secure random session token.
     *
     * @param byteLength the number of random bytes to generate (token hex length = byteLength * 2)
     * @return a SessionToken containing both the plaintext token and its SHA-256 hash
     */
    public static SessionToken generate(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        String hex = bytesToHex(bytes);
        String hash = sha256Hex(hex);
        return new SessionToken(hex, hash);
    }

    /**
     * Compute SHA-256 hex digest of the given plaintext.
     *
     * @param plaintext the string to hash
     * @return lowercase hex-encoded SHA-256 digest
     */
    public static String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JRE
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_CHARS.charAt((b >> 4) & 0x0F));
            sb.append(HEX_CHARS.charAt(b & 0x0F));
        }
        return sb.toString();
    }

    // ── Getters ────────────────────────────────────────────

    /**
     * @return the plaintext hex token — safe to return to the client
     */
    public String getRawToken() {
        return rawToken;
    }

    /**
     * @return the SHA-256 hash of the token — stored in the database
     */
    public String getTokenHash() {
        return tokenHash;
    }
}
