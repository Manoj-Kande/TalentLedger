-- The V1 baseline created the campaigns table without a deleted_at column,
-- even though CampaignEntity maps one and the soft-delete trigger at the
-- end of V1 already references campaigns.deleted_at. Add it here.

ALTER TABLE campaigns ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_campaigns_deleted ON campaigns(deleted_at) WHERE deleted_at IS NULL;
