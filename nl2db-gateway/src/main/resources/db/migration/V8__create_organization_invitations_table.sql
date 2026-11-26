-- V8__create_organization_invitations_table.sql
-- Adds the organization_invitations table within the nl2db schema
-- for the invitation workflow and RBAC integration.
-- Flyway manages the schema_version table automatically;
-- this migration must use a new version number following the last applied migration.
-- Applied migrations are immutable — future schema changes must use new
-- versioned migrations. Do not edit an already-applied migration.
--
-- Design notes:
-- - id uses UUID type stored as VARCHAR(36) to match the users table convention
-- - token_hash stores the SHA-256 hash of the raw invitation token (64 hex chars)
--   The raw token value is never persisted or exposed
-- - organization_id scopes the invitation to a specific organization
-- - email stores the invited user's email address
-- - status tracks the invitation state (PENDING, ACCEPTED, REVOKED, EXPIRED)
-- - Foreign key constraint enforces referential integrity with organizations
-- - Index on organization_id for efficient invitation lookups by organization
-- - Index on email for efficient invitation lookups by email
-- - Unique constraint on token_hash prevents token hash collisions

CREATE TABLE nl2db.organization_invitations (
    id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    inviter_user_id VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT fk_invitations_organization FOREIGN KEY (organization_id) REFERENCES nl2db.organizations (id) ON DELETE CASCADE
);

-- Unique constraint on token hash ensures no duplicate invitation tokens
CREATE UNIQUE INDEX idx_organization_invitations_token_hash ON nl2db.organization_invitations (token_hash);

-- Index for efficient lookups by organization ID (invitation listing)
CREATE INDEX idx_organization_invitations_org_id ON nl2db.organization_invitations (organization_id);

-- Index for efficient lookups by email (invitation acceptance)
CREATE INDEX idx_organization_invitations_email ON nl2db.organization_invitations (email);

-- Index for efficient expired invitation cleanup
CREATE INDEX idx_organization_invitations_expires_at ON nl2db.organization_invitations (expires_at);