package com.bookingsaas.integration.auth;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The integration API's equivalent of {@code CurrentTenant}: every endpoint
 * names the business it acts on and the permission it needs, and both are
 * checked against the token before anything is read.
 */
@Component
public class IntegrationAccess {

    public IntegrationPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof IntegrationPrincipal principal) {
            return principal;
        }
        throw ApiException.of(ErrorCode.UNAUTHENTICATED, "An integration token is required.");
    }

    /** The principal, once it is known to act for this business with this permission. */
    public IntegrationPrincipal require(Long businessId, IntegrationPermission permission) {
        IntegrationPrincipal principal = principal();
        if (!principal.businessId().equals(businessId)) {
            throw ApiException.accessDenied("This token does not give access to business " + businessId + ".");
        }
        if (!principal.may(permission)) {
            throw ApiException.accessDenied("This token does not have the " + permission + " permission.");
        }
        return principal;
    }
}
