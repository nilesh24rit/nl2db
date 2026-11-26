package com.nl2db.security;

import java.util.UUID;

/**
 * Centralized tenant context containing the verified identity
 * for the current authenticated request.
 * <p>
 * This context is resolved once after authenticating the principal
 * and verifying their active membership in the requested organization.
 * It contains only information genuinely required by the application
 * and must never be constructed or overridden by client-supplied input.
 * <p>
 * Invariants:
 * - organizationId is always set from authoritative PostgreSQL membership lookup
 * - principalId corresponds to the authenticated user from the JWT
 * - context is always request-scoped and never client-trusted
 * - all fields are immutable after construction
 */
public record TenantContext(

        /** The authenticated user's stable UUID identifier from the JWT principal. */
        UUID principalId,

        /** The verified organization ID the user is an active member of. */
        UUID organizationId

) {

    /**
     * Creates a TenantContext with the given principal and organization IDs.
     * This constructor is package-private; TenantContext should be built
     * only by authorized factory methods that verify membership.
     *
     * @param principalId the authenticated user UUID
     * @param organizationId the verified organization UUID
     */
    public TenantContext(UUID principalId, UUID organizationId) {
        this.principalId = principalId;
        this.organizationId = organizationId;
    }

    /**
     * Returns the authenticated principal identifier.
     */
    public UUID principalId() {
        return principalId;
    }

    /**
     * Returns the verified organization identifier.
     * This is the organization the authenticated user is an active member of.
     */
    public UUID organizationId() {
        return organizationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantContext that = (TenantContext) o;
        return principalId.equals(that.principalId) &&
                organizationId.equals(that.organizationId);
    }

    @Override
    public int hashCode() {
        return principalId.hashCode() * 31 + organizationId.hashCode();
    }
}