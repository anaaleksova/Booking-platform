package com.bookingsaas.customer;

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
 * A customer of one business.
 *
 * <p>Tenant-scoped by design: the same person booking at a salon and at a
 * dentist is two unrelated rows, so no business can learn anything about
 * another's clientele. Customers have no login in the MVP — a name plus a way
 * to reach them is enough to take a booking.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 40)
    private String phone;

    @Column(length = 320)
    private String email;

    @Column(columnDefinition = "text")
    private String notes;
}
