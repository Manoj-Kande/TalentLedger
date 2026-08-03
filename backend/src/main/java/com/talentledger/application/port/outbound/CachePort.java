package com.talentledger.application.port.outbound;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Outbound port — Cache abstraction (provider-agnostic).
 * ADR-004: Caffeine L1 now, Redis L2 later. Swap adapter, same interface.
 */
public interface CachePort {

    /** Get a cached value. */
    <T> Optional<T> get(String key, Class<T> type);

    /** Put a value in cache with TTL. */
    <T> void put(String key, T value, long ttl, TimeUnit unit);

    /** Remove a specific key. */
    void invalidate(String key);

    /** Remove all keys matching the given prefix pattern. */
    void invalidateByPrefix(String prefix);

    /** Check if a key exists. */
    boolean containsKey(String key);
}
