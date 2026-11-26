package com.nl2db.auth;

/**
 * Invitation status for an organization invitation.
 * Permitted state transitions are enforced by the OrganizationInvitation aggregate.
 *
 * @implNote Instances are effectively enum constants but declared as a
 * class to allow future extension with transition logic without
 * breaking the type hierarchy.
 */
public final class InvitationStatus {

    private final String value;

    public static final InvitationStatus PENDING = new InvitationStatus("pending");
    public static final InvitationStatus ACCEPTED = new InvitationStatus("accepted");
    public static final InvitationStatus EXPIRED = new InvitationStatus("expired");
    public static final InvitationStatus REVOKED = new InvitationStatus("revoked");

    private InvitationStatus(String value) {
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
        InvitationStatus that = (InvitationStatus) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}