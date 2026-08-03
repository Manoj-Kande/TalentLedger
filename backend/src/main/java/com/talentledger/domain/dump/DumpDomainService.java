package com.talentledger.domain.dump;

import com.talentledger.domain.shared.BusinessRule;
import com.talentledger.domain.shared.Result;
import com.talentledger.domain.user.UserQuota;

import java.util.UUID;

/**
 * Domain service for DataDump business logic that spans aggregates.
 *
 * <p>Handles cross-aggregate concerns such as quota enforcement
 * and duplicate detection.
 *
 * <p>Pure Java — zero framework annotations.
 */
public final class DumpDomainService {

    private DumpDomainService() {
        // Static utility — prevent instantiation
    }

    // ── Constants ───────────────────────────────────────────

    private static final int PAID_MAX_ACTIVE_DUMPS = 100;
    private static final int FREE_MAX_ACTIVE_DUMPS = 10;

    // ── Public API ─────────────────────────────────────────

    /**
     * Validate that a dump can be uploaded given the user's quota.
     *
     * <p>Checks:
     * <ol>
     *   <li>File size does not exceed the quota's max file size</li>
     *   <li>Active dump count has not reached the limit</li>
     *   <li>Monthly upload count has not reached the limit</li>
     * </ol>
     *
     * @param dump the DataDump to validate (must not be null)
     * @param quota the user's current quota (must not be null)
     * @return success with the dump, or failure with a human-readable message
     */
    public static Result<DataDump, String> validateFileForUpload(DataDump dump, UserQuota quota) {
        BusinessRule.notNull(dump, "DataDump");
        BusinessRule.notNull(quota, "UserQuota");

        // 1. Check file size
        if (dump.getFileSizeBytes() > quota.getStorageBytesLimit()) {
            long maxMb = quota.getStorageBytesLimit() / (1024 * 1024);
            return Result.failure(
                    "File size (" + dump.getFileSizeBytes() + " bytes) exceeds the limit of "
                            + maxMb + " MB");
        }

        // 2. Check active dump count
        if (quota.getActiveDumpsCount() >= quota.getActiveDumpsLimit()) {
            return Result.failure(
                    "Active dump limit reached (" + quota.getActiveDumpsCount() + "/"
                            + quota.getActiveDumpsLimit() + "). Archive or delete existing dumps first.");
        }

        // 3. Check monthly upload count
        if (quota.getUploadsThisMonthCount() >= quota.getUploadsMonthlyLimit()) {
            return Result.failure(
                    "Monthly upload limit reached (" + quota.getUploadsThisMonthCount() + "/"
                            + quota.getUploadsMonthlyLimit() + "). Upgrade your plan for more uploads.");
        }

        return Result.success(dump);
    }

    /**
     * Check whether the same file has already been uploaded by the user.
     *
     * <p>Delegates to the repository for the actual check. This method
     * provides the domain-level semantic wrapper.
     *
     * @param userId   the user's id (must not be null)
     * @param fileHash SHA-256 hash of the file (must not be null or blank)
     * @param repository the dump repository (must not be null)
     * @return true if a dump with the same file hash exists for this user
     */
    public static boolean isDuplicateUpload(UUID userId, String fileHash, DumpRepository repository) {
        BusinessRule.notNull(userId, "User ID");
        BusinessRule.notNull(fileHash, "File hash");
        BusinessRule.ensure(!fileHash.isBlank(), "File hash must not be blank");
        BusinessRule.notNull(repository, "Dump repository");
        return repository.existsByUserIdAndFileHash(userId, fileHash);
    }
}
