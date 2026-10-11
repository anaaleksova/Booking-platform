package com.bookingsaas.integration.auth;

import com.bookingsaas.common.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An API token for an automation tool, bound to one business. Only its
 * SHA-256 is stored; the token itself is shown once, at creation.
 */
@Entity
@Table(name = "integration_token")
@Getter
@Setter
@NoArgsConstructor
public class IntegrationToken extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 500)
    private String permissions;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public Set<IntegrationPermission> permissionSet() {
        if (permissions == null || permissions.isBlank()) {
            return EnumSet.noneOf(IntegrationPermission.class);
        }
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(IntegrationPermission::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(IntegrationPermission.class)));
    }

    public void setPermissionSet(Set<IntegrationPermission> granted) {
        this.permissions = granted.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
