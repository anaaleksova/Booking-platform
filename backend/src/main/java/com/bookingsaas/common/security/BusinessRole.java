package com.bookingsaas.common.security;

/**
 * A user's role <em>within a particular business</em>.
 *
 * <p>Roles are per-membership, not global: the same person can own one business
 * and be staff at another. Lives in {@code common} because both the auth module
 * (which builds principals) and the business module (which persists
 * memberships) need it, and neither should depend on the other.
 */
public enum BusinessRole {

    /** Full administration of the business. */
    BUSINESS_OWNER,

    /** Limited to their own schedule and the bookings assigned to them. */
    STAFF;

    /** Spring Security authority name, e.g. {@code ROLE_BUSINESS_OWNER}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
