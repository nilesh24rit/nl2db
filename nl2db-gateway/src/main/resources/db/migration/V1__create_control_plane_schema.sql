-- V1__create_control_plane_schema.sql
-- Creates the nl2db dedicated schema for the control-plane database.
-- Flyway manages the schema_version table automatically; this migration
-- establishes the application-owned schema and a minimal verification table.
-- Applied migrations are immutable — future schema changes must use new
-- versioned migrations. Do not edit an already-applied migration.

CREATE SCHEMA IF NOT EXISTS nl2db AUTHORIZATION postgres;

CREATE TABLE nl2db.schema_verification (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL,
    migrated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);