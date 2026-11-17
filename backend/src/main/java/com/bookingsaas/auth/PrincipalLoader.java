package com.bookingsaas.auth;

import com.bookingsaas.common.security.AuthenticatedUser;
import com.bookingsaas.common.security.BusinessMembership;
import com.bookingsaas.common.security.MembershipProvider;
import com.bookingsaas.common.security.PlatformAdmins;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Builds the request principal from persisted state.
 *
 * <p>Memberships are read fresh rather than taken from the token: a user
 * removed from a business, or demoted from owner to staff, loses access on
 * their next request instead of when their token happens to expire.
 */
@Service
public class PrincipalLoader {

    private final UserRepository userRepository;
    private final MembershipProvider membershipProvider;
    private final PlatformAdmins platformAdmins;

    public PrincipalLoader(UserRepository userRepository, MembershipProvider membershipProvider,
                           PlatformAdmins platformAdmins) {
        this.userRepository = userRepository;
        this.membershipProvider = membershipProvider;
        this.platformAdmins = platformAdmins;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser loadByUserId(Long userId, Long requestedBusinessId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User " + userId + " no longer exists"));
        return build(user, requestedBusinessId);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser loadByEmail(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
        return build(user, null);
    }

    private AuthenticatedUser build(User user, Long requestedBusinessId) {
        List<BusinessMembership> memberships = membershipProvider.membershipsOf(user.getId());
        boolean platformAdmin = user.isActive() && platformAdmins.isPlatformAdmin(user.getEmail());
        BusinessMembership active = selectActive(memberships, requestedBusinessId);
        // A platform administrator may act in any business, with owner rights, once they enter it.
        if (platformAdmin && requestedBusinessId != null
                && (active == null || !active.businessId().equals(requestedBusinessId))) {
            active = membershipProvider.platformAccessTo(requestedBusinessId).orElse(active);
        }
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getFullName(),
                user.getPasswordHash(), user.isActive(), memberships, active, platformAdmin, user.getPhone());
    }

    /**
     * A requested business is honoured only if the user actually belongs to it;
     * otherwise the request falls back to the user's default membership rather
     * than silently granting access to a foreign tenant.
     */
    static BusinessMembership selectActive(List<BusinessMembership> memberships, Long requestedBusinessId) {
        if (requestedBusinessId != null) {
            for (BusinessMembership membership : memberships) {
                if (membership.businessId().equals(requestedBusinessId)) {
                    return membership;
                }
            }
        }
        return defaultMembership(memberships);
    }

    /** Owned businesses win over staff memberships, then lowest id for stability. */
    public static BusinessMembership defaultMembership(List<BusinessMembership> memberships) {
        return memberships.stream()
                .min(Comparator.comparing((BusinessMembership m) -> m.isOwner() ? 0 : 1)
                        .thenComparing(BusinessMembership::businessId))
                .orElse(null);
    }
}
