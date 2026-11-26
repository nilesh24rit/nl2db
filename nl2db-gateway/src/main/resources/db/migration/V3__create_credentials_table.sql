-- V3__create_credentials_table.sql
-- Adds the credentials table within the nl2db schema for password credential storage.
-- This migration creates the credential table used by the authentication workflow.
-- Flyway manages the schema_version table automatically; this migration
-- must use a new version number following the last applied migration.
-- Applied migrations are immutable — future schema changes must use new
-- versioned migrations. Do not edit an already-applied migration.
--
-- Design notes:
-- - id uses BIGSERIAL for auto-generated primary key
-- - user_id is a foreign key to the users table with UNIQUE constraint
--   enforcing one credential per user
-- - hash stores the encoded Argon2id hash (includes algorithm, parameters, salt)
-- - hash length of 128 characters accommodates Argon2id encoded hashes
-- - all columns NOT NULL except where explicitly optional (none in this stage)
-- - on delete cascade ensures credential is removed when user is removed

CREATE TABLE nl2db.credentials (
    id BIGSERIAL NOT NULL,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_credentials_user FOREIGN KEY (user_id) REFERENCES nl2db.users (id) ON DELETE CASCADE
);

-- Index for lookups by user_id (unique constraint already creates index,
-- but this explicit index ensures consistent naming across deployments).
CREATE INDEX idx_credentials_user_id ON nl2db.credentials (user_id);