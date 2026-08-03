-- ============================================================
-- V2: Stripe billing wiring
-- subscription_events had no way to dedupe a re-delivered Stripe webhook
-- (Stripe retries on any non-2xx and can deliver the same event twice even
-- on success). Add the provider event id so handlers can no-op on repeats.
-- ============================================================

ALTER TABLE subscription_events
    ADD COLUMN IF NOT EXISTS provider_event_id VARCHAR(255);

-- Partial unique index (ADR-034 pattern): only enforce uniqueness where the
-- column is actually populated, since not every historical event necessarily
-- has one.
CREATE UNIQUE INDEX IF NOT EXISTS idx_subscription_events_provider_event_id
    ON subscription_events(provider_event_id)
    WHERE provider_event_id IS NOT NULL;
