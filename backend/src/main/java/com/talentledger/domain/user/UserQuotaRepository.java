package com.talentledger.domain.user;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port (repository interface) for the UserQuota value object.
 *
 * <p>Implementations live in the infrastructure (outbound) adapter layer.
 * Pure Java interface — zero framework annotations.
 */
public interface UserQuotaRepository {

    /**
     * Find the quota for a given user.
     *
     * @param userId the user's UUID
     * @return the quota, or empty if not found
     */
    Optional<UserQuota> findByUserId(UUID userId);

    /**
     * Persist a UserQuota. May insert or update.
     *
     * @param quota the quota to save (must not be null)
     * @return the persisted quota
     */
    UserQuota save(UserQuota quota);
}
