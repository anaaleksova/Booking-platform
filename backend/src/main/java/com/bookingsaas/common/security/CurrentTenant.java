package com.bookingsaas.common.security;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The single place the application asks "which tenant is this request for?".
 *
 * <p>The answer always comes from the authenticated principal, never from a
 * path variable or request body. That is what makes
 * {@code GET /api/businesses/2/bookings} impossible to abuse: there is no code
 * path where a client-supplied business id becomes the tenant.
 */
@Component
public class CurrentTenant {

    public Optional<AuthenticatedUser> optionalUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthenticatedUser user ? Optional.of(user) : Optional.empty();
    }

    public AuthenticatedUser user() {
        return optionalUser()
                .orElseThrow(() -> ApiException.of(ErrorCode.UNAUTHENTICATED, "Authentication is required."));
    }

    public Long userId() {
        return user().userId();
    }

    public BusinessMembership membership() {
        return user().activeMembership()
                .orElseThrow(() -> ApiException.of(ErrorCode.NO_BUSINESS_CONTEXT,
                        "This account is not associated with a business."));
    }

    /** The tenant id every business-scoped repository query must be filtered by. */
    public Long businessId() {
        return membership().businessId();
    }

    public boolean isOwner() {
        return user().activeMembership().map(BusinessMembership::isOwner).orElse(false);
    }

    public void requireOwner() {
        if (!isOwner()) {
            throw ApiException.accessDenied("Only a business owner may perform this action.");
        }
    }
}
