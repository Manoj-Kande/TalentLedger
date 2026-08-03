package com.talentledger.infrastructure.web.controller;

import com.talentledger.infrastructure.persistence.repository.JpaSystemConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Config Controller — public read access to feature flags and system configs.
 */
@RestController
@RequestMapping("/api/v1/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final JpaSystemConfigRepository systemConfigRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPublicConfigs(HttpServletRequest httpRequest) {
        List<com.talentledger.infrastructure.persistence.entity.SystemConfigEntity> configs =
                systemConfigRepository.findAll();

        // Filter to only expose feature flags (not internal configs)
        Map<String, Object> featureFlags = new java.util.HashMap<>();
        for (var config : configs) {
            String key = config.getKey();
            if (key.startsWith("feature.") || key.startsWith("free.") || key.startsWith("pro.")) {
                try {
                    Object value = config.getValue();
                    if (value instanceof String s) {
                        // Try to parse JSON feature flags
                        if (s.startsWith("{")) {
                            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            featureFlags.put(key, mapper.readValue(s, Map.class));
                        } else {
                            featureFlags.put(key, s);
                        }
                    } else {
                        featureFlags.put(key, value);
                    }
                } catch (Exception e) {
                    featureFlags.put(key, config.getValue());
                }
            }
        }

        return ResponseEntity.ok(Map.of("success", true, "data", featureFlags));
    }
}
