package com.bookingsaas.resource;

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
 * A resource's own weekly schedule, narrowing the business opening hours —
 * an employee's shifts, a machine only available in the mornings.
 *
 * <p>No rows for a resource means "available whenever the business is open",
 * the common case, so a new resource is reservable without extra setup.
 */
@Entity
@Table(name = "resource_hours")
@Getter
@Setter
@NoArgsConstructor
public class ResourceHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    /** ISO-8601 day number: 1 = Monday … 7 = Sunday. */
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ResourceHour(Long businessId, Long resourceId, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.businessId = businessId;
        this.resourceId = resourceId;
        this.dayOfWeek = (short) dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
