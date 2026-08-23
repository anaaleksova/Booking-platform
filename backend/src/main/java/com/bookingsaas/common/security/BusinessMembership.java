package com.bookingsaas.common.security;

/** One tenant a user belongs to, and the role they hold there. */
public record BusinessMembership(Long businessId, String businessName, String businessSlug, BusinessRole role) {

    public boolean isOwner() {
        return role == BusinessRole.BUSINESS_OWNER;
    }
}
