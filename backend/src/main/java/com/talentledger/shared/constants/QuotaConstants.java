package com.talentledger.shared.constants;

/**
 * Quota constants per plan tier.
 */
public final class QuotaConstants {

    private QuotaConstants() {}

    // ── FREE ────────────────────────────────────────────────
    public static final int FREE_ACTIVE_DUMPS = 1;
    public static final int FREE_CONTACTS = 1000;
    public static final int FREE_UPLOADS_MONTHLY = 5;
    public static final int FREE_FILE_SIZE_MB = 5;
    public static final int FREE_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    public static final long FREE_STORAGE_BYTES = 100L * 1024 * 1024; // 100 MB, per pricing page
    public static final int FREE_AI_CREDITS = 0;
    public static final int FREE_SESSION_TTL_MINUTES = 120;
    public static final int FREE_EXPORT_MAX_ROWS = 50;
    public static final int FREE_DEMO_TTL_DAYS = 7;

    // ── PRO ────────────────────────────────────────────────
    public static final int PRO_ACTIVE_DUMPS = 999;
    public static final int PRO_CONTACTS = 50000;
    public static final int PRO_UPLOADS_MONTHLY = 100;
    public static final int PRO_FILE_SIZE_MB = 50;
    public static final int PRO_FILE_SIZE_BYTES = 50 * 1024 * 1024;
    public static final long PRO_STORAGE_BYTES = 10L * 1024 * 1024 * 1024; // 10 GB, per pricing page
    public static final int PRO_AI_CREDITS = 500;
    public static final int PRO_SESSION_TTL_MINUTES = 10080;
    public static final int PRO_EXPORT_MAX_ROWS = 10000;

    // ── TEAM ───────────────────────────────────────────────
    public static final int TEAM_CONTACTS = 50000;
    public static final int TEAM_UPLOADS_MONTHLY = 500;
    public static final int TEAM_FILE_SIZE_MB = 100;
    public static final int TEAM_FILE_SIZE_BYTES = 100 * 1024 * 1024;
    public static final int TEAM_AI_CREDITS = 2000;
    public static final int TEAM_SESSION_TTL_MINUTES = 43200;
    public static final int TEAM_EXPORT_MAX_ROWS = 50000;

    // ── ADMIN ───────────────────────────────────────────────
    public static final int ADMIN_ACTIVE_DUMPS = 999;
    public static final int ADMIN_CONTACTS = Integer.MAX_VALUE;
    public static final int ADMIN_UPLOADS_MONTHLY = 999;
    public static final long ADMIN_FILE_SIZE_BYTES = Long.MAX_VALUE;
    public static final int ADMIN_AI_CREDITS = Integer.MAX_VALUE;
}
