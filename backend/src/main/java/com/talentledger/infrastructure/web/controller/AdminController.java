package com.talentledger.infrastructure.web.controller;

import com.talentledger.application.port.inbound.AdminUseCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin Controller — user management, platform stats, audit, config.
 * All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminUseCase adminUseCase;

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        var result = adminUseCase.listUsers(page, size, status, search);
        return toResponse(result);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUserDetail(@PathVariable UUID id) {
        var result = adminUseCase.getUserDetail(id);
        return toResponse(result);
    }

    @PostMapping("/users/{id}/ban")
    public ResponseEntity<Map<String, Object>> banUser(@PathVariable UUID id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        UUID adminId = getCurrentUserId(httpRequest);
        var result = adminUseCase.banUser(adminId, id, body.getOrDefault("reason", "Administrative action"));
        return toResponse(result);
    }

    @PostMapping("/users/{id}/unban")
    public ResponseEntity<Map<String, Object>> unbanUser(@PathVariable UUID id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        UUID adminId = getCurrentUserId(httpRequest);
        var result = adminUseCase.unbanUser(adminId, id, body.getOrDefault("reason", "Administrative action"));
        return toResponse(result);
    }

    /**
     * Change a user's role and/or plan — the admin-managed "grant Premium"
     * flow (no payment provider integration; access is granted manually).
     * Body: {@code { role?, plan?, reason? }}. At least one of role/plan
     * must be present; reason defaults to a generic message if omitted but
     * is always recorded in the audit log.
     */
    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> updateUserAccess(@PathVariable UUID id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        UUID adminId = getCurrentUserId(httpRequest);

        com.talentledger.domain.user.UserRole newRole = null;
        com.talentledger.domain.user.UserPlan newPlan = null;
        try {
            if (body.get("role") != null) {
                newRole = com.talentledger.domain.user.UserRole.valueOf(body.get("role").toUpperCase());
            }
            if (body.get("plan") != null) {
                newPlan = com.talentledger.domain.user.UserPlan.valueOf(body.get("plan").toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", Map.of("code", "INVALID_VALUE", "message", "Unrecognized role or plan: " + e.getMessage())));
        }

        var result = adminUseCase.updateUserAccess(adminId, id, newRole, newPlan,
                body.getOrDefault("reason", "Administrative action"));
        return toResponse(result);
    }

    @PostMapping("/users/{id}/impersonate")
    public ResponseEntity<Map<String, Object>> impersonateUser(@PathVariable UUID id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        UUID adminId = getCurrentUserId(httpRequest);
        var result = adminUseCase.impersonateUser(adminId, id, body.getOrDefault("reason", "Support investigation"));
        return toResponse(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getPlatformStats() {
        var result = adminUseCase.getPlatformStats();
        return toResponse(result);
    }

    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> getAuditLog(
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "50") int limit) {
        var result = adminUseCase.getAuditLog(userId, limit);
        return toResponse(result);
    }

    @GetMapping("/configs")
    public ResponseEntity<Map<String, Object>> getConfigs() {
        var result = adminUseCase.getConfigs();
        return toResponse(result);
    }

    @PutMapping("/configs/{key}")
    public ResponseEntity<Map<String, Object>> updateConfig(@PathVariable String key,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        UUID adminId = getCurrentUserId(httpRequest);
        var result = adminUseCase.updateConfig(key, body.get("value"), adminId);
        return toResponse(result);
    }

    private ResponseEntity<Map<String, Object>> toResponse(AdminUseCase.AdminResult result) {
        return switch (result) {
            case AdminUseCase.Success s -> ResponseEntity.ok(Map.of("success", true, "data", s.data()));
            case AdminUseCase.NotFound n -> ResponseEntity.status(404).body(Map.of("success", false, "error", Map.of("message", n.message())));
            case AdminUseCase.Forbidden f -> ResponseEntity.status(403).body(Map.of("success", false, "error", Map.of("message", f.message())));
            case AdminUseCase.Error e -> {
                int status = (e.code().equals("REASON_REQUIRED") || e.code().equals("INVALID_KEY")
                        || e.code().equals("NO_CHANGES") || e.code().equals("INVALID_VALUE")) ? 400 : 500;
                yield ResponseEntity.status(status).body(Map.of("success", false, "error", Map.of("code", e.code(), "message", e.message())));
            }
        };
    }

    private UUID getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new com.talentledger.shared.exception.UnauthorizedException(
                    "No authenticated user on request (SessionAuthFilter should have rejected this earlier)");
        }
        return UUID.fromString(userId.toString());
    }
}
