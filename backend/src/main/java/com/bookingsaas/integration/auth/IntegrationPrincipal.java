package com.bookingsaas.integration.auth;

import java.util.EnumSet;
import java.util.Set;

/**
 * Who is calling the integration API: a token, the one business it belongs
 * to, and what it may do there. Never a user — an integration token grants
 * no admin rights at all.
 *
 * <p>The platform's own n8n is the one exception: its token belongs to no
 * business ({@code businessId} null) and may act for any of them.
 */
public record IntegrationPrincipal(Long tokenId, Long businessId, String tokenName,
                                   Set<IntegrationPermission> permissions, boolean platform) {

    public IntegrationPrincipal(Long tokenId, Long businessId, String tokenName,
                                Set<IntegrationPermission> permissions) {
        this(tokenId, businessId, tokenName, permissions, false);
    }

    public static IntegrationPrincipal platformAutomation() {
        return new IntegrationPrincipal(null, null, "platform", Set.copyOf(EnumSet.allOf(IntegrationPermission.class)),
                true);
    }

    public boolean may(IntegrationPermission permission) {
        return permissions.contains(permission);
    }

    /** Whether this caller may act for the business at all. */
    public boolean actsFor(Long otherBusinessId) {
        return platform || businessId.equals(otherBusinessId);
    }
}
