package com.talentledger.domain.auth;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable value object holding a TOTP MFA secret.
 *
 * <p>Contains:
 * <ul>
 *   <li>{@code secretKey} - Base32-encoded TOTP secret</li>
 *   <li>{@code encryptedSecret} - encrypted version for storage (actual encryption in infrastructure)</li>
 *   <li>{@code qrCodeUrl} - otpauth://totp URI for QR scanning</li>
 *   <li>{@code backupCodes} - 10 one-time-use alphanumeric codes</li>
 * </ul>
 */
public final class MfaSecret {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int BACKUP_CODE_LENGTH = 8;
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int SECRET_BYTE_LENGTH = 20;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String secretKey;
    private final String encryptedSecret;
    private final String qrCodeUrl;
    private final List<String> backupCodes;

    private MfaSecret(String secretKey, String encryptedSecret, String qrCodeUrl, List<String> backupCodes) {
        this.secretKey = secretKey;
        this.encryptedSecret = encryptedSecret;
        this.qrCodeUrl = qrCodeUrl;
        this.backupCodes = backupCodes;
    }

    public static MfaSecret generate(String email) {
        byte[] secretBytes = new byte[SECRET_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(secretBytes);

        Base32Encoder encoder = new Base32Encoder();
        String base32Secret = encoder.encode(secretBytes);

        String encodedEmail = java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
        String qrCodeUrl = "otpauth://totp/TalentLedger:" + encodedEmail + "?secret=" + base32Secret + "&issuer=TalentLedger";

        java.util.List<String> backupCodes = new java.util.ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            backupCodes.add(generateBackupCode());
        }

        String encryptedSecret = base32Secret;

        return new MfaSecret(base32Secret, encryptedSecret, qrCodeUrl, backupCodes);
    }

    /** Generate a fresh batch of backup codes without generating a new TOTP secret. */
    public static List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            codes.add(generateBackupCode());
        }
        return codes;
    }

    private static String generateBackupCode() {
        StringBuilder sb = new StringBuilder(BACKUP_CODE_LENGTH);
        for (int i = 0; i < BACKUP_CODE_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(SECURE_RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    public String getSecretKey() { return secretKey; }
    public String getEncryptedSecret() { return encryptedSecret; }
    public String getQrCodeUrl() { return qrCodeUrl; }
    public List<String> getBackupCodes() { return backupCodes; }

    private static final class Base32Encoder {

        private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        private static final int MASK = 0x1F;

        String encode(byte[] data) {
            StringBuilder sb = new StringBuilder();
            int buffer = 0;
            int bufferBits = 0;

            for (byte b : data) {
                buffer = (buffer << 8) | (b & 0xFF);
                bufferBits += 8;

                while (bufferBits >= 5) {
                    bufferBits -= 5;
                    sb.append(ALPHABET[(buffer >>> bufferBits) & MASK]);
                }
            }

            if (bufferBits > 0) {
                sb.append(ALPHABET[(buffer << (5 - bufferBits)) & MASK]);
            }

            return sb.toString();
        }
    }
}
