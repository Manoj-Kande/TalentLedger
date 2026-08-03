package com.talentledger.infrastructure.security.mfa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the AES-256-GCM MFA secret encryption added this session.
 * {@code mfa_secret_encrypted} is only a meaningful column name if this
 * actually works — these tests exist specifically to catch a regression
 * back to the previous no-op "encryption" (plaintext passthrough).
 */
class MfaEncryptionServiceTest {

    private final MfaEncryptionService encryptionService = new MfaEncryptionService("test-key-for-unit-tests-only");

    @Test
    void encryptThenDecrypt_returnsTheOriginalPlaintext() {
        String secret = "JBSWY3DPEHPK3PXP";

        String encrypted = encryptionService.encrypt(secret);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(secret);
    }

    @Test
    void encrypt_neverReturnsThePlaintextVerbatim() {
        String secret = "JBSWY3DPEHPK3PXP";

        String encrypted = encryptionService.encrypt(secret);

        assertThat(encrypted).doesNotContain(secret);
    }

    @Test
    void encrypt_producesADifferentCiphertextEachTime() {
        // Random IV per encryption — same plaintext, same key, must not
        // produce the same ciphertext twice (rules out ECB-style bugs).
        String secret = "JBSWY3DPEHPK3PXP";

        String first = encryptionService.encrypt(secret);
        String second = encryptionService.encrypt(secret);

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptionService.decrypt(first)).isEqualTo(secret);
        assertThat(encryptionService.decrypt(second)).isEqualTo(secret);
    }

    @Test
    void decrypt_withWrongKey_fails() {
        MfaEncryptionService otherService = new MfaEncryptionService("a-completely-different-key");
        String encrypted = encryptionService.encrypt("JBSWY3DPEHPK3PXP");

        assertThatThrownBy(() -> otherService.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }
}
