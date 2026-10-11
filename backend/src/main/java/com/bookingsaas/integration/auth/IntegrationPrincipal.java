package com.bookingsaas.integration.auth;

import java.util.Set;

/**
 * Who is calling the integration API: a token, the one business it belongs
 * to, and what it may do there. Never a user — an integration token grants
 * no admin rights at all.
 */
public record IntegrationPrincipal(Long tokenId, Long businessId, String tokenName,
                                   Set<IntegrationPermission> permissions) {

    public boolean may(IntegrationPermission permission) {
        return permissions.contains(permission);
    }
}
