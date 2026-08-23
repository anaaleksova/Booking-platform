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

/**
 * "This staff member can deliver this service" — the many-to-many join that
 * stops a customer booking a haircut with someone who only does manicures.
 */
@Entity
@Table(name = "staff_services")
@Getter
@Setter
@NoArgsConstructor
public class StaffServiceLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    public StaffServiceLink(Long staffId, Long serviceId) {
        this.staffId = staffId;
        this.serviceId = serviceId;
    }
}
