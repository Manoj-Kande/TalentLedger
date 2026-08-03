package com.talentledger.infrastructure.adapter.outbound;

import com.talentledger.application.port.outbound.StoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local filesystem storage adapter for development.
 * Saves files to a configurable local directory.
 * ADR-005: Swap provider via config in under 30 minutes.
 */
@Slf4j
@Component
public class LocalStorageAdapter implements StoragePort {

    @Value("${talentledger.storage.local.path:./uploads}")
    private String storagePath;

    public LocalStorageAdapter() {
        log.info("Using LOCAL storage adapter (dev mode)");
    }

    @Override
    public String upload(String key, InputStream data, long size, String contentType) {
        try {
            Path dir = Paths.get(storagePath);
            Files.createDirectories(dir);
            Path filePath = dir.resolve(key);
            Files.createDirectories(filePath.getParent());
            Files.copy(data, filePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("File uploaded: {} ({} bytes)", key, size);
            return filePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file: " + key, e);
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            Path filePath = Paths.get(storagePath, key);
            if (!Files.exists(filePath)) {
                return null;
            }
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to download file: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path filePath = Paths.get(storagePath, key);
            Files.deleteIfExists(filePath);
            log.info("File deleted: {}", key);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(Paths.get(storagePath, key));
    }

    @Override
    public String getPresignedUrl(String key, long expiryMinutes) {
        return "file://" + Paths.get(storagePath, key).toAbsolutePath();
    }

    @Override
    public String getProviderName() {
        return "LOCAL";
    }
}
