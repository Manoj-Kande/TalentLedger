-- ============================================================
-- V3: Guest upload preview flow (item #1)
--
-- Guests get a real (but ephemeral) row in `users` so the ENTIRE existing
-- authenticated pipeline — session auth, quota enforcement, dump upload,
-- parsing, contact CRUD/search — works for them completely unmodified.
-- No new parser code path, no nullable user_id anywhere downstream. Guest
-- rows are deleted by ScheduledJobs.purgeExpiredGuestAccounts once
-- guest_expires_at passes; ON DELETE CASCADE on data_dumps/contacts etc.
-- (already in place from V1) cleans up everything they created.
-- ============================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_guest BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS guest_expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_users_guest_expiry
    ON users(guest_expires_at)
    WHERE is_guest = TRUE;
