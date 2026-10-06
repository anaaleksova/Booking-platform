package com.bookingsaas.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A password-reset code waiting to be used. One per account; asking again replaces it. */
@Entity
@Table(name = "password_reset")
@Getter
@Setter
@NoArgsConstructor
public class PasswordReset {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** SHA-256 hex of the six-digit code. */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
}
