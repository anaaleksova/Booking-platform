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
import java.time.LocalTime;

/**
 * A staff member's own weekly schedule, overriding the business hours.
 *
 * <p>No rows for a staff member means "works whenever the business is open" —
 * the common case, and it keeps a new business bookable without per-person
 * configuration.
 */
@Entity
@Table(name = "staff_working_hours")
@Getter
@Setter
@NoArgsConstructor
public class StaffWorkingHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    /** ISO-8601 day number: 1 = Monday … 7 = Sunday. */
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public StaffWorkingHour(Long businessId, Long staffId, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.businessId = businessId;
        this.staffId = staffId;
        this.dayOfWeek = (short) dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
