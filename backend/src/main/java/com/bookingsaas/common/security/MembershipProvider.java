package com.bookingsaas.common.security;

import java.util.List;

/**
 * Lets the auth module build a principal without depending on the business
 * module's persistence code. The business module owns memberships and supplies
 * the only implementation.
 */
public interface MembershipProvider {

    List<BusinessMembership> membershipsOf(Long userId);
}
