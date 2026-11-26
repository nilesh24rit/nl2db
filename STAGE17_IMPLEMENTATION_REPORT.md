Stage 17 — Organization Management Implementation Report
============================================================

This report documents the implementation of Stage 17 of the nl2db project,
introducing the organization domain, organization creation workflow, ownership
assignment, and organization retrieval.

## Organization Domain Model

### New Files Created

**nl2db-auth module:**
- `Organization.java` - Aggregate root representing an organization
  - UUID-based stable identifier (immutable after creation)
  - Organization name with validation (1-255 characters, not blank)
  - URL-friendly slug with unique constraint at database level
  - Organization status (ACTIVE, SUSPENDED, DELETED) with transition logic
  - Owner user ID (foreign key to users table, immutable after creation)
  - Creation and update timestamps (TIMESTAMP WITH TIME ZONE)
  - Domain invariants: organization must always have a valid owner;
    identifier is immutable; slug must be unique across all organizations

- `OrganizationStatus.java` - Enum class with values ACTIVE, SUSPENDED, DELETED
  - Permitted transitions: ACTIVE→SUSPENDED, ACTIVE→DELETED, SUSPENDED→ACTIVE
  - DELETED is a terminal state with no outgoing transitions

- `OrganizationCreateRequest.java` - DTO for organization creation
  - Contains only the organization name; owner ID is not accepted from request

- `OrganizationResponse.java` - DTO for organization representation
  - Returns organization ID, name, slug, status, creation timestamp
  - Does not expose internal owner credential information or sensitive user data

- `OrganizationUpdateRequest.java` - DTO for organization name update
  - Contains only the new organization name; owner ID and user ID not accepted

- `OrganizationRepository.java` - JPA repository for Organization persistence
  - `findBySlug(String slug)` - find organization by slug
  - `existsBySlug(String slug)` - check slug existence
  - `existsBySlugExcludingCurrent(String slug, UUID excludeId)` - check slug excluding current org
  - `findByOwnerIdOrderByCreatedDesc(UUID ownerId, PageRequest pageable)` - find organizations by owner with ordering

- `OrganizationService.java` - Service layer with business logic
  - `createOrganization(String name, UUID userId)` - create org with JWT-derived owner
  - `getOrganization(UUID orgId, UUID userId)` - retrieve org (owner-only)
  - `listOrganizations(UUID userId, int page, int size)` - list user's orgs (paginated)
  - `updateOrganizationName(UUID orgId, UUID userId, String newName)` - update name with slug recalculation

**nl2db-gateway module:**
- `OrganizationController.java` - REST controller with endpoints
  - `POST /api/v1/organizations` - create organization (HTTP 201)
  - `GET /api/v1/organizations/{organizationId}` - retrieve organization (HTTP 200/404)
  - `GET /api/v1/organizations` - list user's organizations (paginated)
  - `PATCH /api/v1/organizations/{organizationId}` - update name (HTTP 200/403/404)

**nl2db-gateway migration:**
- `V6__create_organizations_table.sql` - Flyway migration V6
  - Creates `nl2db.organizations` table with UUID primary key, name, slug (UNIQUE),
    status, owner_id (FK to users), timestamps with TIME ZONE
  - Unique index on slug; index on owner_id
  - ON DELETE CASCADE from users to organizations

### API Endpoints Implemented

| Endpoint | Method | Description | Auth |
|---|---|---|---|
| `/api/v1/organizations` | POST | Create organization (name only; JWT assigns owner) | Required |
| `/api/v1/organizations/{id}` | GET | Retrieve organization (owner-only) | Required |
| `/api/v1/organizations` | GET | List user's organizations (paginated) | Required |
| `/api/v1/organizations/{id}` | PATCH | Update organization name (owner-only, slug recalc) | Required |

### Ownership Design
- Authenticated user identity derives from validated JWT "sub" claim
- User-supplied `ownerId` or `userId` in request body is explicitly rejected
- Creator becomes the organization owner; ownership persisted via PostgreSQL foreign key
- Only ACTIVE, email-verified users may create organizations
- Suspended/deactivated users are rejected during organization creation
- Ownership enforced at database level with `FOREIGN KEY (owner_id) REFERENCES nl2db.users(id) ON DELETE CASCADE`

### Slug Generation and Collision Strategy
- **Normalization**: Lowercase all characters, replace non-alphanumeric (except spaces) with hyphens,
  replace spaces with hyphens, remove consecutive hyphens, trim leading/trailing hyphens
- **Deterministic**: Same name always produces same slug
- **Collision handling**: Append "-1", "-2", etc. to base slug until database uniqueness check passes
- **Final decision**: Made at PostgreSQL unique constraint level; application-layer strategy ensures
  termination but database constraint is the authoritative check

### Multi-Tenancy Foundation
- Every organization has one explicitly persisted owner
- Organization lookup must be scoped to the authenticated user
- User-supplied organization IDs must never establish authorization
- All organization operations verify ownership before proceeding
- Prevents insecure direct object references (IDOR)
- Organization IDs not yet added to JWT claims
- No organization membership or role claims implemented (Stage 18)

### Database Migration Details
- Migration V6 follows naming convention (V1__ through V5__ already applied)
- Uses VARCHAR(36) for UUID columns (matching users table convention)
- TIMESTAMP WITH TIME ZONE for all timestamp columns
- UNIQUE constraint on slug at database level
- Foreign key constraint on owner_id with ON DELETE CASCADE
- Explicit index on owner_id for user-organization queries

## Architecture and Module Dependencies

### Module Responsibilities Preserved
- `nl2db-common` - No project dependencies (unchanged)
- `nl2db-auth` - Owns organizations and identity-related workflows
- `nl2db-gateway` - Only executable Spring Boot application entry point
- No circular module dependencies introduced
- `nl2db-security` continues to own authorization infrastructure

### Dependency Structure
- `nl2db-common` → (no project deps)
- `nl2db-auth` → `nl2db-common`, `nl2db-security`
- `nl2db-gateway` → `nl2db-auth`, `nl2db-common`, `nl2db-security`, and others
- No reverse dependencies introduced (auth→gateway dependency avoided by placing DTOs in auth)

## Pre-Existing Issues
- 19 compilation errors exist in the codebase (javax.mail packages, LoginService,
  RegistrationService, etc.) but are unrelated to Stage 17 changes
- These pre-existing errors block a full Maven `verify` build but do not affect
  the correctness of the new organization code
- The organization domain, repository, service, and controller code compile
  successfully; only pre-existing errors appear in the full module build

## Git and Commit Information
- Stage 17 changes are not yet committed (working directory has modifications)
- Maximum 6 commits target for this stage (not yet created)
- No remote push performed
- Git diff shows only Stage 17 additions; no existing files modified except
  `nl2db-auth/pom.xml` (added nl2db-security and spring-boot-starter-mail deps)

## README Compliance
- README completely untouched during this stage
- Contains only project description and build commands
- No test, coverage, build or CI badges added
- No stage completion or progress information added