package com.talentledger.infrastructure.security.mfa;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the RFC 6238 TOTP implementation added this session.
 * Verifies correctness against a manually-computed reference code (not just
 * self-consistency), plus the clock-skew tolerance and rejection behavior.
 */
class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    // A fixed, known secret used purely for deterministic test vectors —
    // never used for anything real.
    private static final String TEST_SECRET_BASE32 = "JBSWY3DPEHPK3PXP";

    @Test
    void verifyCode_acceptsACorrectlyComputedCode() throws Exception {
        long counter = Instant.now().getEpochSecond() / 30;
        String expectedCode = computeReferenceCode(TEST_SECRET_BASE32, counter);

        assertThat(totpService.verifyCode(TEST_SECRET_BASE32, expectedCode)).isTrue();
    }

    @Test
    void verifyCode_rejectsAnIncorrectCode() {
        assertThat(totpService.verifyCode(TEST_SECRET_BASE32, "000000")).isFalse();
    }

    @Test
    void verifyCode_rejectsNonNumericOrWrongLengthInput() {
        assertThat(totpService.verifyCode(TEST_SECRET_BASE32, "abcdef")).isFalse();
        assertThat(totpService.verifyCode(TEST_SECRET_BASE32, "12345")).isFalse();
        assertThat(totpService.verifyCode(TEST_SECRET_BASE32, "1234567")).isFalse();
        assertThat(totpService.verifyCode(TEST_SECRET_BASE32, null)).isFalse();
    }

    @Test
    void verifyCode_toleratesOneStepOfClockSkew() throws Exception {
        long currentCounter = Instant.now().getEpochSecond() / 30;
        String previousStepCode = computeReferenceCode(TEST_SECRET_BASE32, currentCounter - 1);
        String nextStepCode = computeReferenceCode(TEST_SECRET_BASE32, currentCounter + 1);

        assertThat(totpService.verifyCode(TEST_SECRET_BASE32, previousStepCode)).isTrue();
        assertThat(totpService.verifyCode(TEST_SECRET_BASE32, nextStepCode)).isTrue();
    }

    @Test
    void verifyCode_rejectsCodeTwoStepsOutOfWindow() throws Exception {
        long currentCounter = Instant.now().getEpochSecond() / 30;
        String farCode = computeReferenceCode(TEST_SECRET_BASE32, currentCounter + 2);

        assertThat(totpService.verifyCode(TEST_SECRET_BASE32, farCode)).isFalse();
    }

    /**
     * Independent reference implementation of RFC 6238/4226, used only to
     * generate expected test values — deliberately not sharing code with
     * {@link TotpService} so a bug in the real implementation can't also be
     * baked into the test's expectations.
     */
    private String computeReferenceCode(String base32Secret, long counter) throws Exception {
        byte[] key = base32Decode(base32Secret);
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(counterBytes);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % 1_000_000;
        return String.format("%06d", otp);
    }

    private byte[] base32Decode(String base32) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String cleaned = base32.trim().toUpperCase().replace("=", "");
        byte[] result = new byte[cleaned.length() * 5 / 8];
        long buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char c : cleaned.toCharArray()) {
            int value = alphabet.indexOf(c);
            if (value < 0) continue;
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
