-- V9__add_owner_memberships_for_existing_organizations.sql
-- Migrates existing organizations to have OWNER membership records.
-- This migration runs after V7 (organization_memberships table) and V8 (organization_invitations table).
-- Applied migrations are immutable — future schema changes must use new
-- versioned migrations. Do not edit an already-applied migration.
--
-- Design notes:
-- - Creates OWNER membership for every existing organization using the owner_id from the organizations table
-- - Uses INSERT IGNORE to safely handle any edge cases without failing the entire migration
-- - The membership status is set to ACTIVE
-- - The membership role is set to OWNER
-- - No organization should end up without an owner after this migration
-- - Existing owner identity is preserved (owner_id already exists in organizations table)
-- - Idempotent: can be re-run without creating duplicate memberships (thanks to unique constraint)

-- Insert OWNER membership for all organizations that don't already have one
INSERT INTO nl2db.organization_memberships (id, organization_id, user_id, role, status, created_at, updated_at)
SELECT
    UUID_TO_BIN_TO_UUID(NEWID()), -- Generate new UUID for membership
    o.id,
    o.owner_id,
    'owner',
    'active',
    o.created_at,
    o.updated_at
FROM nl2db.organizations o
WHERE NOT EXISTS (
    -- Skip if an membership already exists for this organization-user pair
    SELECT 1 FROM nl2db.organization_memberships m
    WHERE m.organization_id = o.id
      AND m.user_id = o.owner_id
);

-- Verify that every organization has an owner membership
SELECT COUNT(*) AS organizations_without_owner,
       (SELECT COUNT(*) FROM nl2db.organization_memberships m
        WHERE m.organization_id IN (SELECT id FROM nl2db.organizations)
          AND m.role = 'owner') AS owner_memberships_count;