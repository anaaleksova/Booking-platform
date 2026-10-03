package com.bookingsaas.employee;

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

/**
 * A person who works at the business.
 *
 * <p>Employees carry people data (contact details, an optional login), but they
 * are reserved exactly like any other resource: every employee is backed by
 * one {@code resource} row of the business's employee type. A service that
 * needs "2 × Employee" is therefore handled by the same engine, the same
 * conflict checks and the same database constraint as "1 × Treatment Room" —
 * there is no second reservation mechanism for people.
 *
 * <p>{@code userId} is nullable because most employees never log in. The
 * employee's photo is the image of their resource.
 */
@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
public class Employee extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "resource_id", nullable = false, unique = true)
    private Long resourceId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 320)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;
}
