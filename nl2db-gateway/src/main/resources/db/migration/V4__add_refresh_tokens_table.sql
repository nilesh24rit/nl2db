-- V4__add_refresh_tokens_table.sql
-- Adds the refresh_tokens table within the nl2db schema for refresh token persistence.
-- Flyway manages the schema_version table automatically; this migration
-- must use a new version number following the last applied migration.
-- Applied migrations are immutable — future schema changes must use new
-- versioned migrations. Do not edit an already-applied migration.
--
-- Design notes:
-- - id uses UUID type stored as VARCHAR(36) to match the users table convention
-- - user_id is a foreign key to the users table, enforcing ownership
-- - token_hash stores the SHA-256 hash of the raw refresh token (64 hex chars)
-- - family_id groups tokens into a rotation family for secure rotation and revocation
-- - issued_at uses TIMESTAMP WITH TIME ZONE for timezone-aware storage
-- - expires_at defines the absolute lifetime of the token family
-- - consumed_at is set when the token is rotated/replaced
-- - revoked_at is set when the family is revoked (logout or reuse detection)
-- - replacement_token_id links a consumed token to its replacement for traceability
-- - All timestamps use timezone-aware database types
-- - Unique constraint on token_hash prevents token hash collisions
-- - Index on user_id and family_id for efficient revocation operations
-- - Raw token value is never persisted; only the SHA-256 hash is stored

CREATE TABLE nl2db.refresh_tokens (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replacement_token_id VARCHAR(36),
    revocation_reason VARCHAR(100),
    PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES nl2db.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_family FOREIGN KEY (replacement_token_id) REFERENCES nl2db.refresh_tokens (id) ON DELETE SET NULL
);

-- Unique constraint on token hash ensures no duplicate hash entries
CREATE UNIQUE INDEX idx_refresh_tokens_token_hash ON nl2db.refresh_tokens (token_hash);

-- Index for efficient lookups by user ID (revocation operations)
CREATE INDEX idx_refresh_tokens_user_id ON nl2db.refresh_tokens (user_id);

-- Index for efficient family revocation operations
CREATE INDEX idx_refresh_tokens_family_id ON nl2db.refresh_tokens (family_id);

-- Index for efficient expired token cleanup
CREATE INDEX idx_refresh_tokens_expires_at ON nl2db.refresh_tokens (expires_at);