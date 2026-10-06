package com.bookingsaas.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A customer sign-up waiting for the code emailed to it. Holds the already
 * hashed password, so the plaintext never has to be sent twice; the code itself
 * is stored only as a hash.
 */
@Entity
@Table(name = "pending_signup")
@Getter
@Setter
@NoArgsConstructor
public class PendingSignup {

    /** Lower-cased, like {@link User#getEmail()}. */
    @Id
    @Column(length = 320)
    private String email;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

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
