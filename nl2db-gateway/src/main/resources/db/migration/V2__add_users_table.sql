-- V2__add_users_table.sql
-- Adds the users table within the nl2db schema for the User aggregate.
-- This migration is independent of authentication/registration workflows
-- and only establishes the persistence schema for the User domain.
--
-- Design notes:
-- - id uses UUID type stored as VARCHAR(36) to avoid PostgreSQL UUID extension dependency
--   and keep the migration self-contained. Alternative: UUID column with
--   nl2db extension if project adopts it later.
-- - normalized_email has a UNIQUE constraint and index for enforcement and lookups.
-- - created_at and updated_at use TIMESTAMP WITH TIME ZONE for timezone-aware storage.
-- - status stores the UserStatus enum value as VARCHAR.
-- - all columns NOT NULL except where explicitly optional (none in this stage).

CREATE TABLE nl2db.users (
    id VARCHAR(36) NOT NULL,
    normalized_email VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'pending_verification',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_normalized_email UNIQUE (normalized_email)
);

-- Index for lookups by normalized email (unique constraint already creates index,
-- but this explicit index ensures consistent naming across deployments).
CREATE INDEX idx_users_normalized_email ON nl2db.users (normalized_email);