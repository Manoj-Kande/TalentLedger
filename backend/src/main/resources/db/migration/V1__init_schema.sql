-- ============================================================
-- TalentLedger — V1: Initial Schema (Production v1.1)
-- Every table, index, trigger, constraint from Master Architecture
-- ============================================================

-- ── Extensions ────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ============================================================
-- SECTION 1: USERS & IDENTITY
-- ============================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_id VARCHAR(255) UNIQUE,
    email VARCHAR(255) UNIQUE NOT NULL,
    email_verified BOOLEAN DEFAULT FALSE,
    name VARCHAR(255),
    avatar_url TEXT,
    role VARCHAR(20) DEFAULT 'USER' CHECK (role IN ('USER', 'PREMIUM', 'ADMIN')),
    plan VARCHAR(20) DEFAULT 'FREE' CHECK (plan IN ('FREE', 'PRO', 'TEAM', 'ENTERPRISE')),
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED', 'PENDING_DELETION', 'DELETED')),
    password_hash VARCHAR(255),
    onboarding_completed BOOLEAN DEFAULT FALSE,
    onboarding_profile JSONB DEFAULT '{}',
    failed_login_attempts INT DEFAULT 0,
    account_locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    last_login_ip VARCHAR(45),
    last_login_user_agent TEXT,
    mfa_enabled BOOLEAN DEFAULT FALSE,
    mfa_type VARCHAR(20) CHECK (mfa_type IN ('TOTP', 'SMS', 'EMAIL')),
    mfa_secret_encrypted TEXT,
    mfa_setup_completed_at TIMESTAMPTZ,
    mfa_backup_codes_remaining INT DEFAULT 0,
    timezone VARCHAR(50) DEFAULT 'UTC',
    locale VARCHAR(10) DEFAULT 'en-US',
    email_notifications JSONB DEFAULT '{"marketing":true,"security":true,"product":true}',
    accepted_terms_at TIMESTAMPTZ,
    accepted_privacy_at TIMESTAMPTZ,
    deletion_requested_at TIMESTAMPTZ,
    data_export_requested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_user_delete_sync CHECK ((deleted_at IS NULL) OR (status IN ('PENDING_DELETION','DELETED'))),
    CONSTRAINT chk_user_name_not_empty CHECK (name IS NULL OR LENGTH(TRIM(name)) > 0)
);

CREATE INDEX idx_users_clerk ON users(clerk_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_mfa ON users(mfa_enabled) WHERE mfa_enabled = TRUE;
CREATE INDEX idx_users_locked ON users(account_locked_until) WHERE account_locked_until IS NOT NULL;

-- ============================================================
-- SECTION 2: USER QUOTAS
-- ============================================================

CREATE TABLE user_quotas (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    active_dumps_count INT DEFAULT 0 CHECK (active_dumps_count >= 0),
    active_dumps_limit INT DEFAULT 1 CHECK (active_dumps_limit >= 0),
    contacts_stored_count INT DEFAULT 0 CHECK (contacts_stored_count >= 0),
    contacts_stored_limit INT DEFAULT 0 CHECK (contacts_stored_limit >= 0),
    uploads_this_month_count INT DEFAULT 0 CHECK (uploads_this_month_count >= 0),
    uploads_monthly_limit INT DEFAULT 5 CHECK (uploads_monthly_limit >= 0),
    ai_credits_used INT DEFAULT 0 CHECK (ai_credits_used >= 0),
    ai_credits_limit INT DEFAULT 0 CHECK (ai_credits_limit >= 0),
    storage_bytes_used BIGINT DEFAULT 0 CHECK (storage_bytes_used >= 0),
    storage_bytes_limit BIGINT DEFAULT 5242880 CHECK (storage_bytes_limit >= 0),
    has_active_free_dump BOOLEAN DEFAULT FALSE,
    last_reset_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- SECTION 3: SESSIONS & LOGIN HISTORY
-- ============================================================

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_token_hash VARCHAR(255) UNIQUE NOT NULL,
    clerk_session_id VARCHAR(255),
    device_name VARCHAR(255),
    device_type VARCHAR(50),
    browser VARCHAR(100),
    os VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent TEXT,
    device_fingerprint VARCHAR(64),
    country_code VARCHAR(2),
    city VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    last_active_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(50),
    is_trusted BOOLEAN DEFAULT FALSE,
    trusted_until TIMESTAMPTZ,
    is_impersonation BOOLEAN DEFAULT FALSE,
    impersonated_by_admin_id UUID REFERENCES users(id) ON DELETE SET NULL,
    impersonation_reason TEXT
);

CREATE INDEX idx_sessions_user_active ON user_sessions(user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_sessions_token ON user_sessions(session_token_hash);

CREATE TABLE login_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    attempted_email VARCHAR(255),
    attempt_type VARCHAR(20) NOT NULL CHECK (attempt_type IN ('OAUTH_GOOGLE','OAUTH_GITHUB','PASSWORD','API_KEY','IMPERSONATION')),
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent TEXT,
    device_fingerprint VARCHAR(64),
    country_code VARCHAR(2),
    city VARCHAR(100),
    mfa_required BOOLEAN DEFAULT FALSE,
    mfa_passed BOOLEAN,
    session_id UUID REFERENCES user_sessions(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_login_user ON login_history(user_id, created_at DESC);
CREATE INDEX idx_login_ip ON login_history(ip_address, created_at DESC);
CREATE INDEX idx_login_failed ON login_history(user_id, success) WHERE success = FALSE;

-- ============================================================
-- SECTION 4: NATIVE AUTH TABLES
-- ============================================================

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    used_ip VARCHAR(45),
    user_agent_at_creation TEXT,
    ip_address_at_creation VARCHAR(45),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_reset_tokens_hash ON password_reset_tokens(token_hash) WHERE used_at IS NULL;
CREATE INDEX idx_reset_tokens_expiry ON password_reset_tokens(expires_at) WHERE used_at IS NULL;

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    ip_address_at_creation VARCHAR(45),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_email_verification_hash ON email_verification_tokens(token_hash) WHERE verified_at IS NULL;
CREATE INDEX idx_email_verification_expiry ON email_verification_tokens(expires_at) WHERE verified_at IS NULL;

CREATE TABLE password_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_password_history_user ON password_history(user_id, created_at DESC);

-- ============================================================
-- SECTION 5: MFA & DEVICES
-- ============================================================

CREATE TABLE mfa_backup_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    code_hint VARCHAR(4) NOT NULL,
    used_at TIMESTAMPTZ,
    used_ip VARCHAR(45),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, code_hash)
);

CREATE INDEX idx_backup_codes_user ON mfa_backup_codes(user_id) WHERE used_at IS NULL;

CREATE TABLE user_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_fingerprint VARCHAR(64) NOT NULL,
    device_name VARCHAR(255),
    device_type VARCHAR(50),
    browser VARCHAR(100),
    os VARCHAR(100),
    is_trusted BOOLEAN DEFAULT FALSE,
    trusted_at TIMESTAMPTZ,
    trust_expires_at TIMESTAMPTZ,
    is_2fa_trusted BOOLEAN DEFAULT FALSE,
    mfa_trusted_at TIMESTAMPTZ,
    mfa_trust_expires_at TIMESTAMPTZ,
    first_seen_at TIMESTAMPTZ DEFAULT NOW(),
    first_seen_ip VARCHAR(45),
    first_seen_country VARCHAR(2),
    last_seen_at TIMESTAMPTZ DEFAULT NOW(),
    last_seen_ip VARCHAR(45),
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(50),
    UNIQUE (user_id, device_fingerprint)
);

CREATE INDEX idx_devices_user ON user_devices(user_id, last_seen_at DESC);
CREATE INDEX idx_devices_2fa ON user_devices(user_id, is_2fa_trusted) WHERE is_2fa_trusted = TRUE;

CREATE TABLE email_change_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    old_email VARCHAR(255) NOT NULL,
    new_email VARCHAR(255) NOT NULL,
    changed_by VARCHAR(50) NOT NULL CHECK (changed_by IN ('USER','CLERK_WEBHOOK','ADMIN')),
    changed_by_admin_id UUID REFERENCES users(id) ON DELETE SET NULL,
    verified_at TIMESTAMPTZ,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- SECTION 6: SUBSCRIPTIONS
-- ============================================================

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan VARCHAR(20) DEFAULT 'FREE' CHECK (plan IN ('FREE','PRO','TEAM','ENTERPRISE')),
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CANCELLED','EXPIRED','PAST_DUE','TRIAL')),
    billing_cycle VARCHAR(20) CHECK (billing_cycle IN ('MONTHLY','YEARLY')),
    started_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    payment_provider VARCHAR(20),
    provider_subscription_id VARCHAR(255),
    provider_customer_id VARCHAR(255),
    invoice_url TEXT,
    amount_cents INT,
    currency VARCHAR(3) DEFAULT 'USD',
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_user ON subscriptions(user_id, status);
CREATE INDEX idx_subscriptions_expires ON subscriptions(expires_at) WHERE status = 'ACTIVE';

CREATE TABLE subscription_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('CREATED','RENEWED','UPGRADED','DOWNGRADED','CANCELLED','PAYMENT_FAILED','PAYMENT_SUCCEEDED')),
    from_plan VARCHAR(20),
    to_plan VARCHAR(20),
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_subscription_events_sub ON subscription_events(subscription_id, created_at DESC);

-- ============================================================
-- SECTION 7: DATA DUMPS
-- ============================================================

CREATE TABLE data_dumps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    tags JSONB DEFAULT '[]',
    original_filename VARCHAR(500) NOT NULL,
    file_type VARCHAR(20) NOT NULL CHECK (file_type IN ('CSV','XLSX','XLS','PDF','JSON','TXT')),
    file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes >= 0),
    file_hash VARCHAR(64),
    column_mapping JSONB DEFAULT '{}',
    column_mapping_confidence DECIMAL(3,2),
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','PARSING','COMPLETED','FAILED','CANCELLED','EXPIRED')),
    total_rows INT DEFAULT 0 CHECK (total_rows >= 0),
    parsed_contacts_count INT DEFAULT 0 CHECK (parsed_contacts_count >= 0),
    live_contacts_count INT DEFAULT 0 CHECK (live_contacts_count >= 0),
    duplicate_within_dump_count INT DEFAULT 0 CHECK (duplicate_within_dump_count >= 0),
    cross_dump_duplicate_count INT DEFAULT 0 CHECK (cross_dump_duplicate_count >= 0),
    error_count INT DEFAULT 0 CHECK (error_count >= 0),
    parse_errors JSONB DEFAULT '[]',
    parse_duration_ms INT,
    is_pinned BOOLEAN DEFAULT FALSE,
    is_archived BOOLEAN DEFAULT FALSE,
    is_persisted BOOLEAN DEFAULT FALSE,
    storage_provider VARCHAR(20) DEFAULT 'R2',
    original_file_key VARCHAR(500),
    parsed_snapshot_key VARCHAR(500),
    original_file_deleted_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_dump_delete_sync CHECK ((deleted_at IS NULL) OR (status IN ('EXPIRED','DELETED'))),
    CONSTRAINT chk_dump_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE INDEX idx_dumps_user_active ON data_dumps(user_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_dumps_expires ON data_dumps(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_dumps_user_hash ON data_dumps(user_id, file_hash);
CREATE INDEX idx_dumps_pinned ON data_dumps(user_id, is_pinned DESC, is_archived, deleted_at, created_at DESC);
CREATE INDEX idx_dumps_expired_cleanup ON data_dumps(is_persisted, expires_at) WHERE is_persisted = false;

-- ============================================================
-- SECTION 8: COMPANIES
-- ============================================================

CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_name VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    category VARCHAR(20) CHECK (category IN ('PRODUCT','IT_SERVICES','OTHER')),
    industry VARCHAR(100),
    size_range VARCHAR(50),
    headquarters VARCHAR(100),
    domain VARCHAR(255),
    logo_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_companies_category ON companies(category);
CREATE INDEX idx_companies_domain ON companies(domain) WHERE domain IS NOT NULL;

-- ============================================================
-- SECTION 9: CONTACTS (The Golden Table)
-- ============================================================

CREATE TABLE contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    primary_dump_id UUID REFERENCES data_dumps(id) ON DELETE SET NULL,
    company_id UUID REFERENCES companies(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    normalized_email VARCHAR(255),
    phone VARCHAR(50),
    linkedin_url VARCHAR(500),
    secondary_email VARCHAR(255),
    title VARCHAR(255),
    department VARCHAR(100),
    seniority_level VARCHAR(20) CHECK (seniority_level IN ('IC','MANAGER','DIRECTOR','VP','CXO','FOUNDER')),
    location VARCHAR(100),
    timezone VARCHAR(50),
    language VARCHAR(50),
    domain VARCHAR(255),
    verification_score INT DEFAULT 0 CHECK (verification_score BETWEEN 0 AND 100),
    last_activity_date DATE,
    source_url TEXT,
    source VARCHAR(50) DEFAULT 'csv',
    notes TEXT,
    tags JSONB DEFAULT '[]',
    custom_fields JSONB DEFAULT '{}',
    ai_enrichment JSONB DEFAULT '{}',
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED','DELETED')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_contact_delete_sync CHECK ((deleted_at IS NULL) OR (status = 'DELETED')),
    CONSTRAINT chk_contact_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

-- B-tree indexes
CREATE INDEX idx_contacts_user_status ON contacts(user_id, status, deleted_at, name);
CREATE INDEX idx_contacts_company ON contacts(company_id) WHERE company_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_contacts_domain ON contacts(domain) WHERE domain IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_contacts_seniority ON contacts(seniority_level) WHERE seniority_level IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_contacts_location ON contacts(location) WHERE location IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_contacts_created ON contacts(user_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_contacts_normalized_email ON contacts(normalized_email) WHERE deleted_at IS NULL;
CREATE INDEX idx_contacts_primary_dump ON contacts(primary_dump_id) WHERE primary_dump_id IS NOT NULL;
CREATE INDEX idx_contacts_linkedin ON contacts(linkedin_url) WHERE linkedin_url IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_contacts_phone ON contacts(phone) WHERE phone IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_contacts_name_prefix ON contacts(name text_pattern_ops) WHERE deleted_at IS NULL;

-- GIN FTS indexes (field-segregated — ADR-008)
CREATE INDEX idx_contacts_fts_name_active ON contacts USING gin(to_tsvector('english', COALESCE(name,''))) WHERE deleted_at IS NULL;
CREATE INDEX idx_contacts_fts_title_active ON contacts USING gin(to_tsvector('english', COALESCE(title,''))) WHERE deleted_at IS NULL;
CREATE INDEX idx_contacts_fts_notes_active ON contacts USING gin(to_tsvector('english', COALESCE(notes,''))) WHERE deleted_at IS NULL;

-- GIN JSONB indexes
CREATE INDEX idx_contacts_tags ON contacts USING gin(tags) WHERE deleted_at IS NULL;
CREATE INDEX idx_contacts_custom ON contacts USING gin(custom_fields) WHERE deleted_at IS NULL;

-- Trigram index for fuzzy name search (ADR-037)
CREATE INDEX idx_contacts_trgm_name ON contacts USING gin(name gin_trgm_ops) WHERE deleted_at IS NULL;

-- Partial unique indexes (ADR-034 — CREATE UNIQUE INDEX, not inline)
CREATE UNIQUE INDEX idx_contacts_user_email_active ON contacts(user_id, email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX idx_contacts_user_email_normalized_active ON contacts(user_id, normalized_email) WHERE deleted_at IS NULL;

-- ============================================================
-- SECTION 10: DUMP CONTACTS JUNCTION
-- ============================================================

CREATE TABLE dump_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dump_id UUID NOT NULL REFERENCES data_dumps(id) ON DELETE CASCADE,
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    row_number INT,
    raw_data JSONB,
    is_duplicate_within_dump BOOLEAN DEFAULT FALSE,
    is_cross_dump_duplicate BOOLEAN DEFAULT FALSE,
    matched_existing_contact_id UUID REFERENCES contacts(id) ON DELETE SET NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_dump_contacts_contact ON dump_contacts(contact_id);
CREATE INDEX idx_dump_contacts_dupes ON dump_contacts(dump_id, is_duplicate_within_dump, is_cross_dump_duplicate);
CREATE UNIQUE INDEX idx_dump_contacts_active ON dump_contacts(dump_id, contact_id) WHERE deleted_at IS NULL;

-- ============================================================
-- SECTION 11: CONTACT VERSIONS (Audit Trail)
-- ============================================================

CREATE TABLE contact_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    changed_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    change_type VARCHAR(20) NOT NULL CHECK (change_type IN ('CREATE','UPDATE','DELETE','RESTORE','BULK_UPDATE','AI_ENRICH','MERGE')),
    field_changed JSONB,
    old_values JSONB,
    new_values JSONB,
    change_reason VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_contact_versions_contact ON contact_versions(contact_id, created_at DESC);

-- ============================================================
-- SECTION 12: AI LAYER (Before Campaigns for FK ordering)
-- ============================================================

CREATE TABLE ai_prompts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    prompt_type VARCHAR(20) CHECK (prompt_type IN ('COLD_EMAIL','DATA_EXTRACTION','PROFILE_SUMMARY','HIRING_SIGNAL','CUSTOM')),
    content TEXT NOT NULL,
    variables_json JSONB,
    model_config JSONB,
    is_system BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    usage_count INT DEFAULT 0 CHECK (usage_count >= 0),
    avg_tokens_per_call INT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_ai_prompt_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE INDEX idx_ai_prompts_deleted ON ai_prompts(deleted_at) WHERE deleted_at IS NULL;

CREATE TABLE ai_enrichments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    prompt_id UUID REFERENCES ai_prompts(id) ON DELETE SET NULL,
    enrichment_type VARCHAR(20) CHECK (enrichment_type IN ('EMAIL_DRAFT','HIRING_SIGNAL','PROFILE_SUMMARY','BEST_ANGLE','SENTIMENT')),
    model_used VARCHAR(50),
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    generated_content TEXT,
    confidence_score DECIMAL(3,2),
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- SECTION 13: SAVED LISTS
-- ============================================================

CREATE TABLE saved_lists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    filters_json JSONB,
    is_dynamic BOOLEAN DEFAULT FALSE,
    contact_count INT DEFAULT 0 CHECK (contact_count >= 0),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_list_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE INDEX idx_saved_lists_user ON saved_lists(user_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE saved_list_contacts (
    list_id UUID NOT NULL REFERENCES saved_lists(id) ON DELETE CASCADE,
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ DEFAULT NOW(),
    added_reason VARCHAR(50),
    PRIMARY KEY (list_id, contact_id)
);

-- ============================================================
-- SECTION 14: CAMPAIGNS & OUTREACH
-- ============================================================

CREATE TABLE campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    template_id UUID,
    sequence_json JSONB,
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','PAUSED','COMPLETED','ARCHIVED')),
    total_contacts INT DEFAULT 0 CHECK (total_contacts >= 0),
    sent_count INT DEFAULT 0 CHECK (sent_count >= 0),
    reply_count INT DEFAULT 0 CHECK (reply_count >= 0),
    bounce_count INT DEFAULT 0 CHECK (bounce_count >= 0),
    scheduled_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT chk_campaign_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE INDEX idx_campaigns_user ON campaigns(user_id, status);

-- campaigns.template_id -> ai_prompts.id (FK added after both tables exist)
ALTER TABLE campaigns ADD CONSTRAINT fk_campaigns_template
FOREIGN KEY (template_id) REFERENCES ai_prompts(id) ON DELETE SET NULL;

CREATE TABLE campaign_contacts (
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    contact_id UUID REFERENCES contacts(id) ON DELETE SET NULL,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','REPLIED','BOUNCED','UNSUBSCRIBED')),
    scheduled_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    replied_at TIMESTAMPTZ,
    email_subject VARCHAR(500),
    email_body TEXT,
    metadata JSONB,
    contact_deleted_at TIMESTAMPTZ,
    PRIMARY KEY (campaign_id, contact_id)
);

CREATE TABLE outreach_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_id UUID REFERENCES contacts(id) ON DELETE SET NULL,
    campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('EMAIL_SENT','REPLY_RECEIVED','FOLLOW_UP','NOTE_ADDED','STATUS_CHANGE','EMAIL_COPIED')),
    status VARCHAR(20),
    content TEXT,
    sentiment VARCHAR(20) CHECK (sentiment IN ('POSITIVE','NEUTRAL','NEGATIVE')),
    metadata JSONB,
    occurred_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_outreach_contact ON outreach_events(contact_id, occurred_at DESC);
CREATE INDEX idx_outreach_user ON outreach_events(user_id, event_type);
CREATE INDEX idx_outreach_deleted ON outreach_events(deleted_at) WHERE deleted_at IS NULL;

-- ============================================================
-- SECTION 15: AUDIT & SYSTEM
-- ============================================================

CREATE TABLE user_audit_log (
    id UUID DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(20),
    entity_id UUID,
    metadata JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    request_id UUID,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Default partition required (ADR-035)
CREATE TABLE user_audit_log_default PARTITION OF user_audit_log DEFAULT;
CREATE INDEX idx_audit_user ON user_audit_log(user_id, created_at DESC);
CREATE INDEX idx_audit_request ON user_audit_log(request_id);

CREATE TABLE admin_audit_log (
    id UUID DEFAULT gen_random_uuid(),
    admin_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    target_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    target_entity_type VARCHAR(20),
    target_entity_id UUID,
    old_values JSONB,
    new_values JSONB,
    reason TEXT NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    request_id UUID,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE admin_audit_log_default PARTITION OF admin_audit_log DEFAULT;
CREATE INDEX idx_admin_audit_target ON admin_audit_log(target_user_id);
CREATE INDEX idx_admin_audit_admin ON admin_audit_log(admin_user_id, created_at DESC);

-- ============================================================
-- SECTION 16: SYSTEM CONFIGS
-- ============================================================

CREATE TABLE system_configs (
    "key" VARCHAR(100) PRIMARY KEY,
    value JSONB NOT NULL,
    description TEXT,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Clerk webhook events
CREATE TABLE clerk_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_event_id VARCHAR(255) UNIQUE NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_clerk_webhook_processed ON clerk_webhook_events(processed, created_at) WHERE processed = FALSE;

-- API keys
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_hash VARCHAR(255) UNIQUE NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    name VARCHAR(100),
    scopes JSONB DEFAULT '["read:contacts"]',
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_user ON api_keys(user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_api_keys_expires ON api_keys(expires_at) WHERE revoked_at IS NULL;

-- Webhook idempotency (7-day TTL — ADR-025)
CREATE TABLE webhook_idempotency (
    clerk_event_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ DEFAULT NOW() + INTERVAL '7 days'
);

CREATE INDEX idx_webhook_expires ON webhook_idempotency(expires_at);

-- Generic idempotency keys
CREATE TABLE idempotency_keys (
    key_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_expires ON idempotency_keys(expires_at);

-- ============================================================
-- SECTION 17: OUTBOX & DEAD LETTER
-- ============================================================

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    published BOOLEAN DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    retry_count INT DEFAULT 0 CHECK (retry_count >= 0),
    expires_at TIMESTAMPTZ DEFAULT NOW() + INTERVAL '7 days',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_outbox_unpublished ON outbox_events(published, created_at) WHERE published = FALSE;
CREATE INDEX idx_outbox_expires ON outbox_events(expires_at);

CREATE TABLE dead_letter_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_outbox_id UUID NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    error_message TEXT NOT NULL,
    retry_count INT NOT NULL CHECK (retry_count >= 0),
    expires_at TIMESTAMPTZ DEFAULT NOW() + INTERVAL '30 days',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_dead_letter_created ON dead_letter_events(created_at);
CREATE INDEX idx_dead_letter_expires ON dead_letter_events(expires_at);

-- ============================================================
-- SECTION 18: TRIGGER FUNCTIONS
-- ============================================================

-- Auto-update updated_at (generic — ADR-040)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Dump contact count sync (INSERT/DELETE)
CREATE OR REPLACE FUNCTION update_dump_contact_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE data_dumps
        SET parsed_contacts_count = parsed_contacts_count + 1,
            live_contacts_count = live_contacts_count + 1
        WHERE id = NEW.dump_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE data_dumps
        SET live_contacts_count = live_contacts_count - 1
        WHERE id = OLD.dump_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Dump contact count sync (UPDATE soft-delete — ADR-036)
CREATE OR REPLACE FUNCTION update_dump_contact_count_on_update()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
        UPDATE data_dumps
        SET live_contacts_count = live_contacts_count - 1
        WHERE id = NEW.dump_id;
    ELSIF OLD.deleted_at IS NOT NULL AND NEW.deleted_at IS NULL THEN
        UPDATE data_dumps
        SET live_contacts_count = live_contacts_count + 1
        WHERE id = NEW.dump_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Saved list contact count sync
CREATE OR REPLACE FUNCTION update_saved_list_contact_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE saved_lists SET contact_count = contact_count + 1 WHERE id = NEW.list_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE saved_lists SET contact_count = contact_count - 1 WHERE id = OLD.list_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Campaign contact count sync
CREATE OR REPLACE FUNCTION update_campaign_counts()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE campaigns SET total_contacts = total_contacts + 1 WHERE id = NEW.campaign_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE campaigns SET total_contacts = total_contacts - 1 WHERE id = OLD.campaign_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- User soft delete cascade (EXTENDED)
CREATE OR REPLACE FUNCTION cascade_user_soft_delete()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL THEN
        UPDATE data_dumps SET deleted_at = NEW.deleted_at WHERE user_id = NEW.id AND deleted_at IS NULL;
        UPDATE contacts SET deleted_at = NEW.deleted_at WHERE user_id = NEW.id AND deleted_at IS NULL;
        UPDATE dump_contacts SET deleted_at = NEW.deleted_at WHERE contact_id IN (SELECT id FROM contacts WHERE user_id = NEW.id);
        UPDATE saved_lists SET deleted_at = NEW.deleted_at WHERE user_id = NEW.id AND deleted_at IS NULL;
        UPDATE campaigns SET deleted_at = NEW.deleted_at WHERE user_id = NEW.id AND deleted_at IS NULL;
        UPDATE outreach_events SET deleted_at = NEW.deleted_at WHERE user_id = NEW.id AND deleted_at IS NULL;
        UPDATE ai_prompts SET deleted_at = NEW.deleted_at WHERE user_id = NEW.id AND deleted_at IS NULL;
        UPDATE user_sessions SET revoked_at = NEW.deleted_at, revoke_reason = 'USER_DELETED' WHERE user_id = NEW.id AND revoked_at IS NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- SECTION 19: TRIGGER ATTACHMENTS
-- ============================================================

-- updated_at triggers (ADR-040)
CREATE TRIGGER trigger_contacts_updated BEFORE UPDATE ON contacts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_users_updated BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_saved_lists_updated BEFORE UPDATE ON saved_lists
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_campaigns_updated BEFORE UPDATE ON campaigns
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_ai_prompts_updated BEFORE UPDATE ON ai_prompts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_subscriptions_updated BEFORE UPDATE ON subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_companies_updated BEFORE UPDATE ON companies
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_data_dumps_updated BEFORE UPDATE ON data_dumps
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_system_configs_updated BEFORE UPDATE ON system_configs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Denormalized count triggers
CREATE TRIGGER trigger_dump_contact_count
    AFTER INSERT OR DELETE ON dump_contacts
    FOR EACH ROW EXECUTE FUNCTION update_dump_contact_count();

CREATE TRIGGER trigger_dump_contact_count_update
    AFTER UPDATE OF deleted_at ON dump_contacts
    FOR EACH ROW EXECUTE FUNCTION update_dump_contact_count_on_update();

CREATE TRIGGER trigger_saved_list_count
    AFTER INSERT OR DELETE ON saved_list_contacts
    FOR EACH ROW EXECUTE FUNCTION update_saved_list_contact_count();

CREATE TRIGGER trigger_campaign_contact_count
    AFTER INSERT OR DELETE ON campaign_contacts
    FOR EACH ROW EXECUTE FUNCTION update_campaign_counts();

-- User cascade soft-delete
CREATE TRIGGER trigger_user_soft_delete
    AFTER UPDATE OF deleted_at ON users
    FOR EACH ROW EXECUTE FUNCTION cascade_user_soft_delete();

-- ============================================================
-- SECTION 20: SYSTEM CONFIG SEED DATA
-- ============================================================

INSERT INTO system_configs ("key", value, description) VALUES
('free.max_upload_size_mb', '5', 'Free tier max file size in MB'),
('free.max_rows_per_upload', '200', 'Free tier max contacts per dump'),
('free.session_ttl_minutes', '120', 'Free tier ephemeral session lifetime'),
('free.max_uploads_per_month', '5', 'Free tier upload quota'),
('pro.max_contacts', '10000', 'Pro tier contact limit'),
('pro.max_upload_size_mb', '50', 'Pro tier max file size'),
('system.maintenance_mode', 'false', 'Global maintenance flag'),
('system.ai_enabled', 'true', 'Master AI feature toggle'),
('system.registration_open', 'true', 'Allow new signups'),
('feature.native_auth_enabled', 'false', 'Enable email/password auth'),
('feature.mfa_enabled', 'true', 'Master MFA toggle'),
('security.max_export_contacts', '10000', 'Max contacts per CSV export'),
('security.session_token_bytes', '32', 'Session token entropy in bytes'),
('feature.cold_email_gen', '{"enabled": false, "rollout_percent": 0}', 'Cold email generation feature flag'),
('feature.campaigns', '{"enabled": false, "rollout_percent": 0}', 'Campaigns feature flag'),
('feature.ai_enrichment', '{"enabled": true, "rollout_percent": 100}', 'AI enrichment feature flag');
