package com.bookingsaas.staff;

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

/** An open invitation for an employee to log in. Only a hash of the link's secret is kept. */
@Entity
@Table(name = "staff_invitation")
@Getter
@Setter
@NoArgsConstructor
public class StaffInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "employee_id", nullable = false, unique = true)
    private Long employeeId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "invited_by_user_id")
    private Long invitedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
