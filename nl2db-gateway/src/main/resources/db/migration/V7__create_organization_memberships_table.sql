-- V7__create_organization_memberships_table.sql
-- Adds the organization_memberships table within the nl2db schema
-- for the organization membership and RBAC workflow.
-- Flyway manages the schema_version table automatically;
-- this migration must use a new version number following the last applied migration.
-- Applied migrations are immutable — future schema changes must use new
-- versioned migrations. Do not edit an already-applied migration.
--
-- Design notes:
-- - id uses UUID type stored as VARCHAR(36) to match the users table convention
-- - user_id and organization_id form a unique combination (one membership per user/org)
-- - role stores the membership role (OWNER, ADMIN, MEMBER)
-- - membership_status stores the membership status (ACTIVE, SUSPENDED, REMOVED)
-- - Foreign key constraints enforce referential integrity with users and organizations
-- - ON DELETE CASCADE from users and organizations ensures data consistency
-- - Index on user_id for efficient user-membership lookups
-- - Index on organization_id for efficient organization-member queries
-- - Unique constraint on (user_id, organization_id) prevents duplicate memberships

CREATE TABLE nl2db.organization_memberships (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    membership_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT uk_organization_memberships_user_org UNIQUE (user_id, organization_id),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES nl2db.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_memberships_organization FOREIGN KEY (organization_id) REFERENCES nl2db.organizations (id) ON DELETE CASCADE
);

-- Unique index on (user_id, organization_id) enforces one membership per user-organization pair
CREATE UNIQUE INDEX idx_organization_memberships_user_org ON nl2db.organization_memberships (user_id, organization_id);

-- Index for efficient lookups by user ID (membership queries)
CREATE INDEX idx_organization_memberships_user_id ON nl2db.organization_memberships (user_id);

-- Index for efficient lookups by organization ID (member listing)
CREATE INDEX idx_organization_memberships_org_id ON nl2db.organization_memberships (organization_id);

-- Index for efficient role-based queries
CREATE INDEX idx_organization_memberships_role ON nl2db.organization_memberships (role);