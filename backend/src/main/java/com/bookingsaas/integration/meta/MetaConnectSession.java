package com.bookingsaas.integration.meta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A sign-in waiting for the owner to choose which of their Pages to connect. */
@Entity
@Table(name = "meta_connect_session")
@Getter
@NoArgsConstructor
public class MetaConnectSession {

    @Id
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_token_encrypted", nullable = false, length = 2000)
    private String userTokenEncrypted;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public MetaConnectSession(Long businessId, Long userId, String userTokenEncrypted, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.businessId = businessId;
        this.userId = userId;
        this.userTokenEncrypted = userTokenEncrypted;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }
}
