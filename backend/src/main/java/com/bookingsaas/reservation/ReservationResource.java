package com.bookingsaas.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One concrete resource held by a reservation.
 *
 * <p>The block window and {@code occupying} flag duplicate the reservation's
 * on purpose: the database exclusion constraint that makes double booking
 * impossible can only see this row. {@link Reservation} keeps them in step.
 */
@Entity
@Table(name = "reservation_resource")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "requirement_id")
    private Long requirementId;

    @Column(name = "block_start_time", nullable = false)
    private Instant blockStartTime;

    @Column(name = "block_end_time", nullable = false)
    private Instant blockEndTime;

    @Column(nullable = false)
    private boolean occupying = true;

    /** Seats held on a shared resource; {@code null} when the whole resource is held. */
    @Column
    private Integer seats;

    ReservationResource(Reservation reservation, Long resourceId, Long requirementId, Integer seats) {
        this.reservation = reservation;
        this.businessId = reservation.getBusinessId();
        this.resourceId = resourceId;
        this.requirementId = requirementId;
        this.seats = seats;
        sync();
    }

    /** Copies the window and occupancy from the owning reservation. */
    void sync() {
        this.blockStartTime = reservation.getBlockStartTime();
        this.blockEndTime = reservation.getBlockEndTime();
        this.occupying = reservation.getStatus().occupiesTime();
    }
}
