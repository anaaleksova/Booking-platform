package com.bookingsaas.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The authenticated principal.
 *
 * <p>It carries every membership the user has plus the one resolved for the
 * current request. Granted authorities come from the <em>active</em>
 * membership, so a user who owns business A and is staff at business B gets
 * owner privileges only while acting as A.
 */
public class AuthenticatedUser implements UserDetails {

    private final Long userId;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final boolean active;
    private final List<BusinessMembership> memberships;
    private final BusinessMembership activeMembership;

    public AuthenticatedUser(Long userId,
                             String email,
                             String fullName,
                             String passwordHash,
                             boolean active,
                             List<BusinessMembership> memberships,
                             BusinessMembership activeMembership) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.active = active;
        this.memberships = List.copyOf(memberships);
        this.activeMembership = activeMembership;
    }

    /** Returns a copy of this principal scoped to a different membership. */
    public AuthenticatedUser withActiveMembership(BusinessMembership membership) {
        return new AuthenticatedUser(userId, email, fullName, passwordHash, active, memberships, membership);
    }

    public Long userId() {
        return userId;
    }

    public String email() {
        return email;
    }

    public String fullName() {
        return fullName;
    }

    public List<BusinessMembership> memberships() {
        return memberships;
    }

    public Optional<BusinessMembership> activeMembership() {
        return Optional.ofNullable(activeMembership);
    }

    public Optional<BusinessMembership> membershipFor(Long businessId) {
        return memberships.stream().filter(m -> m.businessId().equals(businessId)).findFirst();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return activeMembership == null
                ? List.of()
                : List.of(new SimpleGrantedAuthority(activeMembership.role().authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
