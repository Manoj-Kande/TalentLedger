package com.talentledger.infrastructure.web.controller;

import com.talentledger.application.dto.request.OnboardingRequest;
import com.talentledger.application.dto.request.UpdateProfileRequest;
import com.talentledger.application.dto.response.UserProfileResponse;
import com.talentledger.application.port.inbound.UserUseCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * User Controller — profile, sessions, devices, login history, stats, onboarding.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserController {

    private final UserUseCase userUseCase;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var uwq = userUseCase.getCurrentUser(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", toProfileResponse(uwq)));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody UpdateProfileRequest request,
                                                            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var uwq = userUseCase.updateProfile(userId, request.name(), request.timezone(), request.locale());
        return ResponseEntity.ok(Map.of("success", true, "data", toProfileResponse(uwq)));
    }

    @PatchMapping("/onboarding")
    public ResponseEntity<Map<String, Object>> updateOnboarding(@RequestBody OnboardingRequest request,
                                                                  HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        userUseCase.updateOnboarding(userId, request.completed(), request.profile());
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("message", "Onboarding updated")));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var stats = userUseCase.getUserStats(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                "totalContacts", stats.totalContacts(),
                "totalCompanies", stats.totalCompanies(),
                "totalDumps", stats.totalDumps(),
                "uploadsThisMonth", stats.uploadsThisMonth(),
                "storageUsedBytes", stats.storageUsedBytes(),
                "storageLimitBytes", stats.storageLimitBytes(),
                "contactsLimit", stats.contactsLimit(),
                "topCompanies", stats.topCompanies()
        )));
    }

    @GetMapping("/activity")
    public ResponseEntity<Map<String, Object>> getActivity(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var activity = userUseCase.getActivity(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", activity));
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        return ResponseEntity.ok(Map.of("success", true, "data", userUseCase.getActiveSessions(userId)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> revokeSession(@PathVariable UUID sessionId,
                                                            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        userUseCase.revokeSession(sessionId, userId);
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("message", "Session revoked")));
    }

    @GetMapping("/login-history")
    public ResponseEntity<Map<String, Object>> getLoginHistory(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        return ResponseEntity.ok(Map.of("success", true, "data", userUseCase.getLoginHistory(userId)));
    }

    @GetMapping("/devices")
    public ResponseEntity<Map<String, Object>> getDevices(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        return ResponseEntity.ok(Map.of("success", true, "data", userUseCase.getDevices(userId)));
    }

    private Map<String, Object> toProfileResponse(UserUseCase.UserWithQuota uwq) {
        var user = uwq.user();
        var quota = uwq.quota();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", user.getId());
        body.put("email", user.getEmail());
        body.put("name", user.getName());
        body.put("role", user.getRole().name());
        body.put("plan", user.getPlan().name());
        body.put("status", user.getStatus().name());
        body.put("emailVerified", user.isEmailVerified());
        body.put("onboardingCompleted", user.isOnboardingCompleted());
        body.put("isGuest", user.isGuest());
        body.put("createdAt", user.getCreatedAt());
        // `quota` was fetched into a local variable and then silently dropped —
        // /api/v1/me never actually returned usage/quota data, which is why the
        // header/settings usage display had nothing to show (item #2).
        if (quota != null) {
            body.put("quotas", Map.of(
                    "activeDumpsCount", quota.getActiveDumpsCount(),
                    "activeDumpsLimit", quota.getActiveDumpsLimit(),
                    "contactsStoredCount", quota.getContactsStoredCount(),
                    "contactsStoredLimit", quota.getContactsStoredLimit(),
                    "uploadsThisMonthCount", quota.getUploadsThisMonthCount(),
                    "uploadsMonthlyLimit", quota.getUploadsMonthlyLimit(),
                    "aiCreditsUsed", quota.getAiCreditsUsed(),
                    "aiCreditsLimit", quota.getAiCreditsLimit(),
                    "storageBytesUsed", quota.getStorageBytesUsed(),
                    "storageBytesLimit", quota.getStorageBytesLimit()
            ));
        }
        return body;
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
