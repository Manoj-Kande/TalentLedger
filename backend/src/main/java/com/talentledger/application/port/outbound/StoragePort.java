package com.talentledger.application.port.outbound;

import java.io.InputStream;

/**
 * Outbound port — File/object storage (provider-agnostic).
 * Implemented by R2StorageAdapter, S3StorageAdapter, or LocalStorageAdapter.
 * ADR-005: Swap provider in under 30 minutes via config.
 */
public interface StoragePort {

    /** Upload a file to the given key path. Returns the public URL or key. */
    String upload(String key, InputStream data, long size, String contentType);

    /** Download a file by key. Returns null if not found. */
    InputStream download(String key);

    /** Delete a file by key. */
    void delete(String key);

    /** Check if a file exists at the given key. */
    boolean exists(String key);

    /** Generate a pre-signed URL for temporary access (optional). */
    String getPresignedUrl(String key, long expiryMinutes);

    /** Get the configured provider name. */
    String getProviderName();
}
