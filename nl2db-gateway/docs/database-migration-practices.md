# Database Migration Practices

## Flyway's Role in the Control-Plane Database

Flyway is the sole mechanism for initializing and managing the control-plane PostgreSQL database schema for the nl2db platform. All schema changes, including the initial setup and future evolutions, must be implemented as versioned Flyway migrations.

- **Flyway uses the existing control-plane datasource** configured via `spring.datasource.*` environment variables (`NL2DB_DB_URL`, `NL2DB_DB_USERNAME`, `NL2DB_DB_PASSWORD`).
- **Migrations run automatically during application startup** in local, test, and production environments.
- **Production environments must never use Hibernate `ddl-auto=update`** as a replacement for Flyway. Hibernate `ddl-auto` is set to `none` to prevent automatic schema modification.
- **Flyway is centralized through the gateway application** (`nl2db-gateway`). No separate migration service or executable module exists.

## Migration Directory Location

```
nl2db-gateway/src/main/resources/db/migration
```

All versioned migration SQL files must be placed in this directory. Flyway automatically discovers migrations from the classpath at this location.

## Migration Naming Convention

Migration files must follow Flyway's versioned migration naming convention:

```
V<version>__<description>.sql
```

- **Versions** are sequential integers starting from `V1`.
- **Descriptions** use lowercase words separated by underscores, describing the migration's purpose.
- **Example**: `V1__create_control_plane_schema.sql`
- **Repeatable migrations** are not used unless there is a concrete requirement.
- **Empty or placeholder migrations are strictly prohibited**.

## How to Create the Next Migration

To add a new migration:

1. **Determine the next version number** by incrementing the highest currently applied migration version (as recorded in Flyware's `schema_version` table).
2. **Create a new SQL file** in `nl2db-gateway/src/main/resources/db/migration/` with the naming convention `V<next_version>__<description>.sql`.
3. **Write PostgreSQL-compatible SQL** that is idempotent where possible (using `IF NOT EXISTS` for schema/table creation).
4. **Do not combine unrelated business features** into a single migration. Each migration should focus on a single logical change.
5. **Add a comment at the top** of the file describing the migration's purpose and any important constraints.
6. **Notify the team** that a new migration has been added and review it as part of the pull request process.

### Example: Creating V2 Migration

```bash
# After V1 is applied, create V2:
copy nl2db-gateway/src/main/resources/db/migration/V1__create_control_plane_schema.sql \
  nl2db-gateway/src/main/resources/db/migration/V2__add_usage_table.sql
```

Then edit `V2__add_usage_table.sql` to add the new schema changes.

## Why Applied Migrations Must Remain Immutable

- **Applied versioned migrations are immutable.** Once a migration is applied to a database, its changes are permanent and must not be modified.
- **Editing an already-applied migration to change an existing database schema is forbidden.** If a schema change is needed, a new migration version must be created that describes the change from the current state.
- **Future schema changes must use new migration versions.** If migration V1 creates a table and V2 needs to modify it, V2 should add new objects or changes, not alter V1's output.
- **The Flyway `schema_history` table tracks which migrations have been applied** to each database. Modifying an applied migration would break this history and cause inconsistent states across environments.

## How Migration Validation Works

Flyway validates migrations in the following ways:

- **`validate-on-migrate=true`** (configured in `application.yml`): Flyway validates the migration scripts against the current database state every time the application starts. If there's a mismatch (e.g., a migration file was deleted or modified), startup will fail.
- **Validation errors stop application startup** when schema initialization is required. This ensures that migration issues are caught early, rather than silently producing incorrect schemas.
- **Do not disable Flyway validation to bypass errors.** Disabling validation can lead to schema drift and inconsistent databases across environments.
- **Do not use `baseline-on-migrate` as a workaround for incorrect migration history.** Baseline is intended for greenfield projects where no prior migration history exists, not for skipping validation.
- **Do not clean or drop production databases automatically.** Flyway's `clean` command exists for explicit, reviewed use cases only, not for automatic cleanup.

### Troubleshooting Migration Failures Safely

If a migration fails during application startup:

1. **Check the Flyware error message** - it typically indicates a SQL syntax error, missing dependency, or schema conflict.
2. **Verify the migration file is syntactically correct** for PostgreSQL. Test the SQL directly against a PostgreSQL instance if possible.
3. **Check that the migration location is correct** and the file naming convention follows `V<version>__<description>.sql`.
4. **Ensure no other migration with the same version exists** in the migration directory.
5. **Review the `schema_version` table** in the database to understand the current migration state.
6. **Do not manually modify the `schema_version` table** to bypass a failed migration. This will cause inconsistent states.
7. **Create a new migration** to fix the issue if the original migration is flawed, following the "new migration version" approach.
8. **Run `./mvnw clean flyway:validate`** to validate migrations without running the full application startup.

## Scope Restrictions

Do not create migrations or tables for the following (these belong to later stages):

- User or organization tables
- Authentication or authorization functionality
- API key tables
- Customer database metadata tables
- Database connector implementations
- Redis, Kafka, or MongoDB integration
- AI functionality, billing, or usage metering
- Business repositories or entities
- New REST endpoints or microservices

## Migration Directory

```
nl2db-gateway/src/main/resources/db/migration/
```

Current migrations:
- `V1__create_control_plane_schema.sql` - Creates the `nl2db` dedicated schema and a minimal verification table.

## Version Matrix

| Migration | Description | Status |
|-----------|-------------|--------|
| V1 | Creates nl2db schema and schema_verification table | Applied on first startup |