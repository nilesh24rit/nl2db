-- V6__create_organizations_table.sql
-- Adds the organizations table within the nl2db schema for the Organization aggregate.
-- This migration creates the organization table used by the ownership and
-- multi-tenancy workflows. Flyway manages the schema_version table automatically;
-- this migration must use a new version number following the last applied migration.
-- Applied migrations are immutable — future schema changes must use new
-- versioned migrations. Do not edit an already-applied migration.
--
-- Design notes:
-- - id uses UUID type stored as VARCHAR(36) to match the users table convention
-- - name stores the organization display name
-- - slug stores the URL-friendly slug, unique across all organizations
-- - status stores the OrganizationStatus enum value as VARCHAR
-- - owner_id is a foreign key to the users table, enforcing ownership
-- - created_at and updated_at use TIMESTAMP WITH TIME ZONE for timezone-aware storage
-- - all columns NOT NULL except where explicitly optional (none in this stage)
-- - unique constraint on slug prevents duplicate slugs at the database level
-- - index on owner_id for efficient user-organization lookups
-- - on delete cascade ensures organizations are removed when owner is removed

CREATE TABLE nl2db.organizations (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    owner_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_organizations_slug UNIQUE (slug),
    CONSTRAINT fk_organizations_owner FOREIGN KEY (owner_id) REFERENCES nl2db.users (id) ON DELETE CASCADE
);

-- Unique index on slug ensures no duplicate slug entries
CREATE UNIQUE INDEX idx_organizations_slug ON nl2db.organizations (slug);

-- Index for efficient lookups by owner ID (user-organization queries)
CREATE INDEX idx_organizations_owner_id ON nl2db.organizations (owner_id);