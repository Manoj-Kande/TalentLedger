package com.talentledger.infrastructure.security.mfa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts/decrypts MFA TOTP secrets before they touch the database.
 *
 * <p>{@code users.mfa_secret_encrypted} is named "encrypted" for a reason —
 * a plaintext TOTP secret leaking (backup dump, read replica, careless log)
 * would let an attacker generate valid codes forever. AES-256-GCM with a
 * random 96-bit nonce per encryption, key derived via SHA-256 from
 * {@code MFA_ENCRYPTION_KEY} so operators can supply any secret string
 * rather than needing to hand-craft an exact-length key.
 */
@Component
public class MfaEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public MfaEncryptionService(@Value("${talentledger.auth.mfa-encryption-key}") String configuredKey) {
        this.key = deriveKey(configuredKey);
    }

    private static SecretKeySpec deriveKey(String configuredKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(configuredKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive MFA encryption key", e);
        }
    }

    /** Encrypt a plaintext TOTP secret. Returns a base64 string safe for a TEXT column. */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Store as: IV (12 bytes) + ciphertext+tag, base64-encoded together.
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("MFA secret encryption failed", e);
        }
    }

    /** Decrypt a value previously produced by {@link #encrypt(String)}. */
    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] ciphertext = new byte[combined.length - GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(combined, GCM_IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("MFA secret decryption failed — was it encrypted with a different key?", e);
        }
    }
}
