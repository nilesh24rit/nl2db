package com.nl2db.auth;

/**
 * Membership status for an organization membership.
 * Permitted state transitions are enforced by the OrganizationMembership aggregate.
 *
 * @implNote Instances are effectively enum constants but declared as a
 * class to allow future extension with transition logic without
 * breaking the type hierarchy.
 */
public final class MembershipStatus {

    private final String value;

    public static final MembershipStatus ACTIVE = new MembershipStatus("active");
    public static final MembershipStatus SUSPENDED = new MembershipStatus("suspended");
    public static final MembershipStatus REMOVED = new MembershipStatus("removed");

    private MembershipStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MembershipStatus that = (MembershipStatus) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}