package com.talentledger.application.service;

import com.talentledger.application.port.inbound.UserUseCase;
import com.talentledger.application.port.inbound.UserUseCase.ActivityEntry;
import com.talentledger.application.port.inbound.UserUseCase.ContactCompanyStat;
import com.talentledger.application.port.inbound.UserUseCase.DeviceInfo;
import com.talentledger.application.port.inbound.UserUseCase.LoginHistoryEntry;
import com.talentledger.application.port.inbound.UserUseCase.SessionInfo;
import com.talentledger.application.port.inbound.UserUseCase.UserStats;
import com.talentledger.application.port.inbound.UserUseCase.UserWithQuota;
import com.talentledger.domain.auth.AuthProviderPort;
import com.talentledger.domain.auth.Session;
import com.talentledger.domain.user.User;
import com.talentledger.domain.user.UserQuota;
import com.talentledger.domain.user.UserQuotaRepository;
import com.talentledger.domain.user.UserRepository;
import com.talentledger.infrastructure.persistence.entity.UserAuditLogEntity;
import com.talentledger.infrastructure.persistence.repository.ContactJpaRepository;
import com.talentledger.infrastructure.persistence.repository.JpaAuditLogRepository;
import com.talentledger.infrastructure.persistence.repository.JpaCampaignRepository;
import com.talentledger.infrastructure.persistence.repository.JpaDataDumpRepository;
import com.talentledger.infrastructure.persistence.repository.JpaUserRepository;
import com.talentledger.infrastructure.persistence.repository.CompanyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application service implementing UserUseCase.
 * Orchestrates user profile, sessions, devices, and login history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService implements UserUseCase {

    private final UserRepository userRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final AuthProviderPort authProviderPort;
    private final JpaUserRepository jpaUserRepository;
    private final ContactJpaRepository contactJpaRepository;
    private final JpaDataDumpRepository jpaDataDumpRepository;
    private final JpaAuditLogRepository jpaAuditLogRepository;

    @Override
    public UserWithQuota getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        UserQuota quota = userQuotaRepository.findByUserId(userId)
                .orElse(null);
        return new UserWithQuota(user, quota);
    }

    @Override
    public List<SessionInfo> getActiveSessions(UUID userId) {
        return authProviderPort.findActiveSessionsByUserId(userId).stream()
                .map(this::toSessionInfo)
                .toList();
    }

    @Override
    public void revokeSession(UUID sessionId, UUID userId) {
        authProviderPort.revokeSession(sessionId, "User-initiated revocation");
        log.info("Session {} revoked by user {}", sessionId, userId);
    }

    @Override
    public List<LoginHistoryEntry> getLoginHistory(UUID userId) {
        return List.of();
    }

    @Override
    public List<DeviceInfo> getDevices(UUID userId) {
        return List.of();
    }

    @Override
    public void revokeDevice(UUID deviceId, UUID userId) {
        log.info("Device {} revoked by user {}", deviceId, userId);
    }

    @Override
    @Transactional
    public UserWithQuota updateProfile(UUID userId, String name, String timezone, String locale) {
        var entity = jpaUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (name != null && !name.isBlank()) {
            entity.setName(name.trim());
        }
        if (timezone != null) {
            entity.setTimezone(timezone);
        }
        if (locale != null) {
            entity.setLocale(locale);
        }
        entity.setUpdatedAt(Instant.now());
        jpaUserRepository.save(entity);

        log.info("Profile updated for user {}: name={}, timezone={}, locale={}", userId, name, timezone, locale);

        User user = userRepository.findById(userId).orElseThrow();
        UserQuota quota = userQuotaRepository.findByUserId(userId).orElse(null);
        return new UserWithQuota(user, quota);
    }

    @Override
    @Transactional
    public void updateOnboarding(UUID userId, boolean completed, Map<String, Object> profile) {
        var entity = jpaUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        entity.setOnboardingCompleted(completed);
        if (profile != null && !profile.isEmpty()) {
            Map<String, Object> existing = new HashMap<>(
                    entity.getOnboardingProfile() != null ? entity.getOnboardingProfile() : Map.of());
            existing.putAll(profile);
            entity.setOnboardingProfile(existing);
        }
        entity.setUpdatedAt(Instant.now());
        jpaUserRepository.save(entity);

        log.info("Onboarding updated for user {}: completed={}", userId, completed);
    }

    @Override
    public UserStats getUserStats(UUID userId) {
        long totalContacts = contactJpaRepository.countByUserIdAndDeletedAtIsNull(userId);
        List<UUID> companyIds = contactJpaRepository.findDistinctCompanyIdsByUserId(userId);
        long totalDumps = jpaDataDumpRepository.countByUserIdAndDeletedAtIsNull(userId);

        // Compute top companies from contacts
        List<ContactCompanyStat> topCompanies = new ArrayList<>();
        Map<String, Long> companyCounts = new HashMap<>();
        contactJpaRepository.findByUserIdNewest(userId, PageRequest.of(0, 1000)).stream()
                .filter(c -> c.getCompanyId() != null)
                .forEach(c -> {
                    String key = c.getCompanyId().toString();
                    companyCounts.merge(key, 1L, Long::sum);
                });
        companyCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> topCompanies.add(new ContactCompanyStat("Company " + e.getKey(), e.getValue())));

        var quota = userQuotaRepository.findByUserId(userId).orElse(null);
        long uploadsThisMonth = quota != null ? quota.getUploadsThisMonthCount() : 0;
        long storageUsedBytes = quota != null ? quota.getStorageBytesUsed() : 0;
        long storageLimitBytes = quota != null ? quota.getStorageBytesLimit() : 0;
        long contactsLimit = quota != null ? quota.getContactsStoredLimit() : 0;

        return new UserStats(totalContacts, companyIds.size(), totalDumps, uploadsThisMonth,
                storageUsedBytes, storageLimitBytes, contactsLimit, topCompanies);
    }

    @Override
    public List<ActivityEntry> getActivity(UUID userId) {
        var page = jpaAuditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 50));
        List<ActivityEntry> entries = new ArrayList<>();
        for (UserAuditLogEntity log : page.getContent()) {
            entries.add(new ActivityEntry(
                    log.getAction() != null ? log.getAction() : "unknown",
                    log.getEntityType() != null ? log.getEntityType() : "unknown",
                    log.getEntityId() != null ? log.getEntityId().toString() : "",
                    log.getCreatedAt()
            ));
        }
        return entries;
    }

    private SessionInfo toSessionInfo(Session session) {
        return new SessionInfo(
                session.getId(),
                session.getDeviceName(),
                session.getDeviceType(),
                session.getBrowser(),
                session.getOs(),
                session.getIpAddress(),
                session.getCountryCode(),
                session.isTrusted(),
                session.isImpersonation(),
                session.getLastActiveAt(),
                session.getExpiresAt(),
                session.getCreatedAt()
        );
    }
}
