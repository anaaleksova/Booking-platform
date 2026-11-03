package com.bookingsaas.auth;

import com.bookingsaas.business.BusinessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A sign-up (a customer's, or a business owner's) waiting for the code emailed to it. Holds the already
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

    /** Set for a business owner's sign-up: confirming the code also creates this business. */
    @Column(name = "business_name", length = 160)
    private String businessName;

    @Column(length = 64)
    private String timezone;

    /** Set for a business owner's sign-up: which template the business starts from. */
    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", length = 20)
    private BusinessType businessType;

    /** ISO-4217, for a business owner's sign-up; null means the default (EUR). */
    @Column(length = 3)
    private String currency;
}
