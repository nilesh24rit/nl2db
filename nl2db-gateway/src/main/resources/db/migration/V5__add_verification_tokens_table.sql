-- V5__add_verification_tokens_table.sql
-- Adds the verification_tokens table within the nl2db schema for verification token persistence.
-- Flyway manages the schema_version table automatically; this migration
-- must use a new version number following the last applied migration.
-- Applied migrations are immutable — future schema changes must use new
-- versioned migrations. Do not edit an already-applied migration.
--
-- Design notes:
-- - id uses UUID type stored as VARCHAR(36) to match the users table convention
-- - user_id is a foreign key to the users table, enforcing ownership
-- - token_hash stores the SHA-256 hash of the raw verification token (64 hex chars)
-- - created_at uses TIMESTAMP WITH TIME ZONE for timezone-aware storage
-- - expires_at defines the absolute lifetime of the token (24 hours)
-- - consumed_at is set when the token is consumed
-- - revoked_at is set when the token is revoked
-- - All timestamps use timezone-aware database types
-- - Unique constraint on token_hash prevents token hash collisions
-- - Index on user_id for efficient lookups and revocation operations
-- - Raw token value is never persisted; only the SHA-256 hash is stored

CREATE TABLE nl2db.verification_tokens (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT fk_verification_tokens_user FOREIGN KEY (user_id) REFERENCES nl2db.users (id) ON DELETE CASCADE,
    CONSTRAINT uk_verification_tokens_token_hash UNIQUE (token_hash)
);

-- Unique index on token hash ensures no duplicate hash entries
CREATE UNIQUE INDEX idx_verification_tokens_token_hash ON nl2db.verification_tokens (token_hash);

-- Index for efficient lookups by user ID (revocation and resend operations)
CREATE INDEX idx_verification_tokens_user_id ON nl2db.verification_tokens (user_id);

-- Index for efficient expired token cleanup
CREATE INDEX idx_verification_tokens_expires_at ON nl2db.verification_tokens (expires_at);