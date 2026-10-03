package com.bookingsaas.service;

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

/**
 * One line of what a service needs: "2 × Employee", "1 × Treatment Room".
 *
 * <p>The availability engine reads these to decide whether a slot is free and
 * which concrete resources to assign.
 */
@Entity
@Table(name = "service_resource_requirement")
@Getter
@Setter
@NoArgsConstructor
public class ServiceResourceRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "resource_type_id", nullable = false)
    private Long resourceTypeId;

    @Column(nullable = false)
    private int quantity = 1;

    /** False: assigned when available, but never makes a slot unavailable. */
    @Column(nullable = false)
    private boolean required = true;

    /** Effective only when the service allows customer resource selection. */
    @Column(name = "customer_can_choose", nullable = false)
    private boolean customerCanChoose;

    /** Display order within the service. */
    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
