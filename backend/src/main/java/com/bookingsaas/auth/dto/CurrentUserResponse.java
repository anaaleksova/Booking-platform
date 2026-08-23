package com.bookingsaas.auth.dto;

import com.bookingsaas.common.security.AuthenticatedUser;
import com.bookingsaas.common.security.BusinessMembership;

import java.util.List;

/** Who the caller is and which tenant they are currently acting for. */
public record CurrentUserResponse(
        Long id,
        String email,
        String fullName,
        MembershipResponse activeBusiness,
        List<MembershipResponse> businesses
) {

    public record MembershipResponse(Long businessId, String businessName, String businessSlug, String role) {

        static MembershipResponse from(BusinessMembership membership) {
            return new MembershipResponse(membership.businessId(), membership.businessName(),
                    membership.businessSlug(), membership.role().name());
        }
    }

    public static CurrentUserResponse from(AuthenticatedUser user) {
        return new CurrentUserResponse(
                user.userId(),
                user.email(),
                user.fullName(),
                user.activeMembership().map(MembershipResponse::from).orElse(null),
                user.memberships().stream().map(MembershipResponse::from).toList());
    }
}
