package com.bookingsaas.common.security;

import java.util.List;
import java.util.Optional;

/**
 * Lets the auth module build a principal without depending on the business
 * module's persistence code. The business module owns memberships and supplies
 * the only implementation.
 */
public interface MembershipProvider {

    List<BusinessMembership> membershipsOf(Long userId);

    /**
     * Owner rights in any existing business, active or not, for a platform
     * administrator entering it. Empty when there is no such business.
     */
    Optional<BusinessMembership> platformAccessTo(Long businessId);
}
