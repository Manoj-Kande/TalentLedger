package com.talentledger.infrastructure.security.mfa;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;

/**
 * TOTP (RFC 6238) code generation and verification.
 *
 * <p>30-second time step, 6-digit codes, HMAC-SHA1 (the standard combination
 * used by Google Authenticator / Authy — matches your architecture doc's
 * "TOTP via authenticator app" requirement). Verification tolerates ±1 time
 * step (±30s) of clock skew per section 8.3 of the master architecture doc.
 */
@Component
public class TotpService {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int CLOCK_SKEW_STEPS = 1;

    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    /**
     * Verify a submitted 6-digit code against a Base32-encoded secret,
     * checking the current time step and one step on either side.
     *
     * @param base32Secret  the decrypted, Base32-encoded TOTP secret
     * @param submittedCode the code the user typed in
     * @return true if the code matches within the allowed clock-skew window
     */
    public boolean verifyCode(String base32Secret, String submittedCode) {
        if (submittedCode == null || !submittedCode.matches("\\d{6}")) {
            return false;
        }
        byte[] key = base32Decode(base32Secret);
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;

        for (long stepOffset = -CLOCK_SKEW_STEPS; stepOffset <= CLOCK_SKEW_STEPS; stepOffset++) {
            String candidate = generateCode(key, currentStep + stepOffset);
            if (constantTimeEquals(candidate, submittedCode)) {
                return true;
            }
        }
        return false;
    }

    private String generateCode(byte[] key, long counter) {
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP code generation failed", e);
        }
    }

    /** Constant-time string comparison to avoid timing side-channels on code verification. */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private byte[] base32Decode(String base32) {
        String cleaned = base32.trim().toUpperCase().replace("=", "");
        int outputLength = cleaned.length() * 5 / 8;
        byte[] result = new byte[outputLength];

        long buffer = 0;
        int bitsLeft = 0;
        int index = 0;

        for (char c : cleaned.toCharArray()) {
            int value = indexOf(c);
            if (value < 0) {
                continue; // skip invalid characters defensively
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return result;
    }

    private int indexOf(char c) {
        for (int i = 0; i < BASE32_ALPHABET.length; i++) {
            if (BASE32_ALPHABET[i] == c) {
                return i;
            }
        }
        return -1;
    }
}
