package com.talentledger.application.service;

import com.talentledger.application.port.inbound.AdminUseCase;
import com.talentledger.application.port.inbound.AdminUseCase.AdminResult;
import com.talentledger.application.port.inbound.AdminUseCase.Error;
import com.talentledger.application.port.inbound.AdminUseCase.NotFound;
import com.talentledger.application.port.inbound.AdminUseCase.Success;
import com.talentledger.domain.auth.AuthProviderPort;
import com.talentledger.domain.auth.Session;
import com.talentledger.domain.user.User;
import com.talentledger.domain.user.UserRepository;
import com.talentledger.domain.user.UserQuota;
import com.talentledger.domain.user.UserQuotaRepository;
import com.talentledger.infrastructure.persistence.entity.AdminAuditLogEntity;
import com.talentledger.infrastructure.persistence.entity.SystemConfigEntity;
import com.talentledger.infrastructure.persistence.repository.JpaAdminAuditLogRepository;
import com.talentledger.infrastructure.persistence.repository.JpaSystemConfigRepository;
import com.talentledger.infrastructure.persistence.repository.JpaUserQuotaRepository;
import com.talentledger.infrastructure.persistence.repository.ContactJpaRepository;
import com.talentledger.infrastructure.persistence.repository.JpaDataDumpRepository;
import com.talentledger.infrastructure.persistence.repository.CompanyJpaRepository;
import com.talentledger.infrastructure.persistence.repository.JpaCampaignRepository;
import com.talentledger.shared.util.SessionTokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application service implementing AdminUseCase.
 * Orchestrates user management, platform stats, audit, and config operations.
 *
 * <p>Every mutating admin action is persisted to {@code admin_audit_log} with
 * a required reason, per section 17 of the master architecture doc
 * ("Service-layer audit logging with required reason field").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService implements AdminUseCase {

    private final UserRepository userRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final AuthProviderPort authProviderPort;
    private final JpaAdminAuditLogRepository adminAuditLogRepository;
    private final JpaSystemConfigRepository systemConfigRepository;
    private final JpaUserQuotaRepository jpaUserQuotaRepository;
    private final ContactJpaRepository contactJpaRepository;
    private final JpaDataDumpRepository jpaDataDumpRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final JpaCampaignRepository jpaCampaignRepository;

    private static final int IMPERSONATION_SESSION_MINUTES = 30;

    @Override
    public AdminResult listUsers(int page, int size, String status, String search) {
        try {
            var allUsers = userRepository.findAll();
            List<Map<String, Object>> userList = allUsers.stream()
                    .filter(u -> status == null || status.isBlank() || u.getStatus().name().equalsIgnoreCase(status))
                    .filter(u -> search == null || search.isBlank()
                            || u.getEmail().toLowerCase().contains(search.toLowerCase())
                            || (u.getName() != null && u.getName().toLowerCase().contains(search.toLowerCase())))
                    .skip((long) page * size)
                    .limit(size)
                    .map(this::toUserMap)
                    .toList();
            return new Success(Map.of("users", userList, "total", allUsers.size(), "page", page, "size", size));
        } catch (Exception e) {
            log.error("Failed to list users", e);
            return new Error("LIST_USERS_FAILED", e.getMessage());
        }
    }

    @Override
    public AdminResult getUserDetail(UUID userId) {
        return userRepository.findById(userId)
                .<AdminResult>map(user -> new Success(toUserMap(user)))
                .orElseGet(() -> new NotFound("User not found: " + userId));
    }

    @Override
    @Transactional
    public AdminResult banUser(UUID adminUserId, UUID userId, String reason) {
        return userRepository.findById(userId)
                .<AdminResult>map(user -> {
                    String previousStatus = user.getStatus().name();
                    user.ban(reason);
                    userRepository.save(user);
                    authProviderPort.revokeAllUserSessions(userId, "Account banned: " + reason);

                    recordAuditLog(adminUserId, "BAN_USER", userId, "USER", userId,
                            Map.of("status", previousStatus), Map.of("status", user.getStatus().name()), reason);

                    log.info("User {} banned by admin {}: {}", userId, adminUserId, reason);
                    return new Success(Map.of("userId", userId, "status", user.getStatus().name()));
                })
                .orElseGet(() -> new NotFound("User not found: " + userId));
    }

    @Override
    @Transactional
    public AdminResult unbanUser(UUID adminUserId, UUID userId, String reason) {
        return userRepository.findById(userId)
                .<AdminResult>map(user -> {
                    String previousStatus = user.getStatus().name();
                    user.unban();
                    userRepository.save(user);

                    recordAuditLog(adminUserId, "UNBAN_USER", userId, "USER", userId,
                            Map.of("status", previousStatus), Map.of("status", user.getStatus().name()), reason);

                    log.info("User {} unbanned by admin {}: {}", userId, adminUserId, reason);
                    return new Success(Map.of("userId", userId, "status", user.getStatus().name()));
                })
                .orElseGet(() -> new NotFound("User not found: " + userId));
    }

    @Override
    @Transactional
    public AdminResult updateUserAccess(UUID adminUserId, UUID userId,
                                         com.talentledger.domain.user.UserRole newRole,
                                         com.talentledger.domain.user.UserPlan newPlan,
                                         String reason) {
        if (reason == null || reason.isBlank()) {
            return new Error("REASON_REQUIRED", "Changing a user's access requires a reason for the audit trail");
        }
        if (newRole == null && newPlan == null) {
            return new Error("NO_CHANGES", "Provide at least one of role or plan to update");
        }

        return userRepository.findById(userId)
                .<AdminResult>map(user -> {
                    Map<String, Object> oldValues = new HashMap<>();
                    Map<String, Object> newValues = new HashMap<>();
                    oldValues.put("role", user.getRole().name());
                    oldValues.put("plan", user.getPlan().name());

                    if (newRole != null && newRole != user.getRole()) {
                        user.changeRole(newRole);
                    }
                    if (newPlan != null && newPlan != user.getPlan()) {
                        user.changePlan(newPlan);

                        // Resync quota limits to the new plan, preserving current
                        // usage counts. Without this, an admin-granted plan change
                        // updates users.plan but the enforced quota row keeps the
                        // old plan's limits (e.g. a Free->Pro upgrade would still
                        // cap uploads/contacts at Free levels until the next
                        // unrelated quota write happened to touch the row).
                        userQuotaRepository.findByUserId(userId).ifPresent(quota -> {
                            UserQuota updatedQuota = quota.changePlan(newPlan);
                            userQuotaRepository.save(updatedQuota);
                        });
                    }

                    userRepository.save(user);

                    newValues.put("role", user.getRole().name());
                    newValues.put("plan", user.getPlan().name());

                    recordAuditLog(adminUserId, "UPDATE_USER_ACCESS", userId, "USER", userId,
                            oldValues, newValues, reason);

                    log.info("User {} access updated by admin {}: role {}->{}, plan {}->{} ({})",
                            userId, adminUserId, oldValues.get("role"), newValues.get("role"),
                            oldValues.get("plan"), newValues.get("plan"), reason);

                    return new Success(toUserMap(user));
                })
                .orElseGet(() -> new NotFound("User not found: " + userId));
    }

    @Override
    @Transactional
    public AdminResult impersonateUser(UUID adminUserId, UUID targetUserId, String reason) {
        if (reason == null || reason.isBlank()) {
            return new Error("REASON_REQUIRED", "Impersonation requires a reason for the audit trail");
        }

        return userRepository.findById(targetUserId)
                .<AdminResult>map(target -> {
                    String rawToken = SessionTokenUtils.generateToken();
                    String tokenHash = SessionTokenUtils.hash(rawToken);
                    Instant expiresAt = Instant.now().plus(IMPERSONATION_SESSION_MINUTES, ChronoUnit.MINUTES);

                    Session session = authProviderPort.createImpersonationSession(
                            targetUserId, adminUserId, tokenHash, expiresAt, reason);

                    recordAuditLog(adminUserId, "IMPERSONATE_USER", targetUserId, "USER", targetUserId,
                            null, Map.of("sessionId", session.getId().toString()), reason);

                    log.warn("Admin {} started impersonating user {} ({}): {}",
                            adminUserId, targetUserId, target.getEmail(), reason);

                    return new Success(Map.of(
                            "sessionToken", rawToken,
                            "adminUserId", adminUserId,
                            "targetUserId", targetUserId,
                            "targetEmail", target.getEmail(),
                            "expiresAt", expiresAt.toString(),
                            "reason", reason));
                })
                .orElseGet(() -> new NotFound("Target user not found: " + targetUserId));
    }

    @Override
    public AdminResult getPlatformStats() {
        try {
            var allUsers = userRepository.findAll();
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", allUsers.size());
            stats.put("activeUsers", allUsers.stream().filter(u -> !u.isDeleted()).count());
            stats.put("bannedUsers", allUsers.stream()
                    .filter(u -> u.getStatus() == com.talentledger.domain.user.UserStatus.BANNED).count());
            stats.put("suspendedUsers", allUsers.stream()
                    .filter(u -> u.getStatus() == com.talentledger.domain.user.UserStatus.SUSPENDED).count());
            stats.put("totalContacts", contactJpaRepository.count());
            stats.put("totalDumps", jpaDataDumpRepository.count());
            stats.put("totalCompanies", companyJpaRepository.count());
            stats.put("activeCampaigns", jpaCampaignRepository.countByStatusAndDeletedAtIsNull(
                    com.talentledger.domain.outreach.CampaignStatus.ACTIVE));
            stats.put("storageUsed", jpaUserQuotaRepository.sumStorageBytesUsedAcrossAllUsers());
            return new Success(stats);
        } catch (Exception e) {
            log.error("Failed to get platform stats", e);
            return new Error("STATS_FAILED", e.getMessage());
        }
    }

    @Override
    public AdminResult getAuditLog(UUID userId, int limit) {
        try {
            var pageable = PageRequest.of(0, Math.min(limit, 200));
            var page = userId != null
                    ? adminAuditLogRepository.findByTargetUserIdOrderByCreatedAtDesc(userId, pageable)
                    : adminAuditLogRepository.findAll(pageable);

            List<Map<String, Object>> entries = page.getContent().stream()
                    .map(this::toAuditMap)
                    .toList();
            return new Success(entries);
        } catch (Exception e) {
            log.error("Failed to load audit log", e);
            return new Error("AUDIT_LOG_FAILED", e.getMessage());
        }
    }

    @Override
    @Transactional
    public AdminResult updateConfig(String key, Object value, UUID adminUserId) {
        if (key == null || key.isBlank()) {
            return new Error("INVALID_KEY", "Config key must not be blank");
        }

        Object previousValue = systemConfigRepository.findById(key).map(SystemConfigEntity::getValue).orElse(null);

        SystemConfigEntity entity = systemConfigRepository.findById(key)
                .orElseGet(() -> SystemConfigEntity.builder().key(key).build());
        entity.setValue(value);
        entity.setUpdatedBy(adminUserId);
        entity.setUpdatedAt(Instant.now());
        systemConfigRepository.save(entity);

        recordAuditLog(adminUserId, "UPDATE_CONFIG", null, "SYSTEM_CONFIG", null,
                previousValue != null ? Map.of("value", previousValue) : null,
                Map.of("value", value), "Config updated via admin portal");

        log.info("Admin {} updated config {}", adminUserId, key);
        return new Success(Map.of("key", key, "value", value, "updatedBy", adminUserId));
    }

    @Override
    public AdminResult getConfigs() {
        Map<String, Object> configs = new HashMap<>();
        systemConfigRepository.findAll().forEach(c -> configs.put(c.getKey(), c.getValue()));
        return new Success(Map.of("configs", configs));
    }

    private void recordAuditLog(UUID adminUserId, String action, UUID targetUserId,
                                 String targetEntityType, UUID targetEntityId,
                                 Map<String, Object> oldValues, Map<String, Object> newValues, String reason) {
        AdminAuditLogEntity entry = AdminAuditLogEntity.builder()
                .adminUserId(adminUserId)
                .action(action)
                .targetUserId(targetUserId)
                .targetEntityType(targetEntityType)
                .targetEntityId(targetEntityId)
                .oldValues(oldValues)
                .newValues(newValues)
                .reason(reason != null ? reason : "No reason provided")
                .createdAt(Instant.now())
                .build();
        adminAuditLogRepository.save(entry);
    }

    private Map<String, Object> toAuditMap(AdminAuditLogEntity e) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", e.getId());
        map.put("adminUserId", e.getAdminUserId());
        map.put("action", e.getAction());
        map.put("targetUserId", e.getTargetUserId());
        map.put("targetEntityType", e.getTargetEntityType());
        map.put("reason", e.getReason());
        map.put("createdAt", e.getCreatedAt());
        return map;
    }

    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("email", user.getEmail());
        map.put("name", user.getName());
        map.put("role", user.getRole().name());
        map.put("plan", user.getPlan().name());
        map.put("status", user.getStatus().name());
        map.put("emailVerified", user.isEmailVerified());
        map.put("createdAt", user.getCreatedAt());
        map.put("updatedAt", user.getUpdatedAt());
        return map;
    }
}
