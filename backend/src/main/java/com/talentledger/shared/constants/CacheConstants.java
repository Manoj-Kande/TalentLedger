package com.talentledger.shared.constants;

/**
 * Cache key constants and TTLs.
 */
public final class CacheConstants {

    private CacheConstants() {}

    // ── TTL (minutes) ──────────────────────────────────────
    public static final long USER_PROFILE_TTL = 5;
    public static final long DUMP_LIST_TTL = 2;
    public static final long SEARCH_TTL = 1;
    public static final long SYSTEM_CONFIG_TTL = 1;

    // ── Key patterns ───────────────────────────────────────
    public static final String USER_PROFILE_KEY = "user:%s:profile";
    public static final String USER_QUOTA_KEY = "user:%s:quota";
    public static final String USER_DUMPS_KEY = "user:%s:dumps";
    public static final String USER_DUMP_KEY = "user:%s:dump:%s";
    public static final String USER_SEARCH_KEY = "user:%s:search:%s";
    public static final String USER_COMPANIES_KEY = "user:%s:companies";
    public static final String SYSTEM_CONFIG_KEY = "system:config:%s";
    public static final String SESSION_CACHE_KEY = "session:%s";
}
