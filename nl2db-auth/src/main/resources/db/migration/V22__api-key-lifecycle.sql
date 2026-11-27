-- Adds API key lifecycle columns to the api_keys table.

ALTER TABLE nl2db.api_keys ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE nl2db.api_keys ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE nl2db.api_keys ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE nl2db.api_keys ADD COLUMN IF NOT EXISTS rotated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE nl2db.api_keys ADD COLUMN IF NOT EXISTS replaced_by_key_id VARCHAR(36);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_api_keys_expiration_after_creation'
    ) THEN
        ALTER TABLE nl2db.api_keys
            ADD CONSTRAINT ck_api_keys_expiration_after_creation
            CHECK (expires_at IS NULL OR expires_at > created_at);
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_api_keys_replaced_by'
    ) THEN
        ALTER TABLE nl2db.api_keys
            ADD CONSTRAINT fk_api_keys_replaced_by
            FOREIGN KEY (replaced_by_key_id) REFERENCES nl2db.api_keys (id)
            ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_api_keys_organization_expires
    ON nl2db.api_keys (organization_id, expires_at);

CREATE INDEX IF NOT EXISTS idx_api_keys_organization_revoked
    ON nl2db.api_keys (organization_id, revoked_at);