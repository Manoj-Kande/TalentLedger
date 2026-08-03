package com.talentledger.infrastructure.scheduling;

import com.talentledger.infrastructure.persistence.entity.DataDumpEntity;
import com.talentledger.infrastructure.persistence.entity.DeadLetterEventEntity;
import com.talentledger.infrastructure.persistence.entity.OutboxEventEntity;
import com.talentledger.infrastructure.persistence.repository.JpaDataDumpRepository;
import com.talentledger.infrastructure.persistence.repository.JpaDeadLetterEventRepository;
import com.talentledger.infrastructure.persistence.repository.JpaOutboxEventRepository;
import com.talentledger.infrastructure.persistence.repository.JpaUserQuotaRepository;
import com.talentledger.infrastructure.persistence.repository.JpaUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Background jobs that were designed (ADRs, entities, repositories all
 * present) but never actually scheduled anywhere — no {@code @Scheduled}
 * method existed in the codebase before this file.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobs {

    private final JpaOutboxEventRepository outboxRepository;
    private final JpaDeadLetterEventRepository deadLetterRepository;
    private final JpaDataDumpRepository dataDumpRepository;
    private final JpaUserQuotaRepository jpaUserQuotaRepository;
    private final JpaUserRepository jpaUserRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    private static final int MAX_RETRIES = 3;

    // ── ADR-042: Outbox poller, every 5s, on the async executor ──────────

    @Scheduled(fixedDelay = 5000)
    @Async("parserExecutor")
    @Transactional
    public void pollOutbox() {
        List<OutboxEventEntity> events = outboxRepository
                .findByPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, 50))
                .getContent();

        for (OutboxEventEntity event : events) {
            try {
                eventPublisher.publishEvent(event);
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= MAX_RETRIES) {
                    deadLetterRepository.save(DeadLetterEventEntity.builder()
                            .originalOutboxId(event.getId())
                            .aggregateType(event.getAggregateType())
                            .aggregateId(event.getAggregateId())
                            .eventType(event.getEventType())
                            .payload(event.getPayload())
                            .errorMessage(e.getMessage() != null ? e.getMessage() : "Unknown error")
                            .retryCount(event.getRetryCount())
                            .createdAt(Instant.now())
                            .build());
                    outboxRepository.delete(event);
                    log.error("Outbox event {} moved to dead letter after {} retries", event.getId(), event.getRetryCount());
                } else {
                    outboxRepository.save(event);
                }
            }
        }
    }

    // ── Guest account expiry (item #1), hourly ─────────────────────────
    // Guests never verify anything, so their whole account is ephemeral
    // (unlike purgeExpiredFreeDumps, which only expires the *dump* for a
    // real, permanent user). Deleting the User row cascades (ON DELETE
    // CASCADE, see V1) through data_dumps/contacts/user_quotas/sessions —
    // everything the guest created disappears in one delete. Their leftover
    // upload files get swept by cleanupOrphanedUploadFiles afterward.

    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void purgeExpiredGuestAccounts() {
        var expired = jpaUserRepository.findByIsGuestTrueAndGuestExpiresAtBefore(Instant.now());
        if (!expired.isEmpty()) {
            jpaUserRepository.deleteAll(expired);
            log.info("Purged {} expired guest accounts", expired.size());
        }
    }

    // ── Free-tier dump expiry (daily, 3 AM) ───────────────────────────────

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredFreeDumps() {
        List<DataDumpEntity> expired = dataDumpRepository.findExpiredFreeDumps(Instant.now());
        for (DataDumpEntity dump : expired) {
            dump.setStatus(com.talentledger.domain.dump.DumpStatus.EXPIRED);
            dump.setDeletedAt(Instant.now());
            dataDumpRepository.save(dump);
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} free-tier dumps past their 7-day TTL", expired.size());
        }
    }

    // ── ADR-031: temp/orphaned upload file cleanup (daily, 3:30 AM) ──────

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupOrphanedUploadFiles() {
        Set<String> referencedPaths = new HashSet<>(dataDumpRepository.findAllOriginalFileKeys());
        String storagePath = System.getProperty("talentledger.storage.local-path",
                System.getenv().getOrDefault("STORAGE_LOCAL_PATH", "./uploads"));
        Path dir = Path.of(storagePath);
        if (!Files.exists(dir)) return;

        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        int deleted = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                try {
                    Instant modified = Files.getLastModifiedTime(file).toInstant();
                    if (modified.isBefore(cutoff) && !referencedPaths.contains(file.toString())) {
                        Files.deleteIfExists(file);
                        deleted++;
                    }
                } catch (IOException e) {
                    log.warn("Could not inspect/delete {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to list upload directory {}: {}", dir, e.getMessage());
        }
        if (deleted > 0) {
            log.info("Cleaned up {} orphaned upload files older than 24h", deleted);
        }
    }

    // ── Monthly quota reset, 1st of month, midnight ───────────────────────

    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void resetMonthlyQuotas() {
        int updated = jpaUserQuotaRepository.resetMonthlyCounters(Instant.now());
        log.info("Monthly quota reset applied to {} users", updated);
    }

    // ── ADR-051: audit table partition automation (25th of month) ────────

    @Scheduled(cron = "0 0 2 25 * *")
    @Transactional
    public void createNextMonthAuditPartitions() {
        Instant nextMonth = Instant.now().plus(7, ChronoUnit.DAYS);
        var start = java.time.LocalDate.now().plusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
        var end = start.plusMonths(1);
        String suffix = start.getYear() + "_" + String.format("%02d", start.getMonthValue());

        createPartitionIfMissing("user_audit_log", suffix, start, end);
        createPartitionIfMissing("admin_audit_log", suffix, start, end);
    }

    private void createPartitionIfMissing(String parentTable, String suffix, java.time.LocalDate start, java.time.LocalDate end) {
        String partitionName = parentTable + "_" + suffix;
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS %s PARTITION OF %s FOR VALUES FROM ('%s') TO ('%s')",
                partitionName, parentTable, start, end);
        try {
            entityManager.createNativeQuery(sql).executeUpdate();
            log.info("Ensured audit partition exists: {}", partitionName);
        } catch (Exception e) {
            // Falls back gracefully — the *_default partition still catches
            // rows if this fails, per ADR-035.
            log.error("Failed to create partition {}: {}", partitionName, e.getMessage());
        }
    }
}
