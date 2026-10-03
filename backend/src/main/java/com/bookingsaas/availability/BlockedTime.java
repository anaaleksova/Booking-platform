package com.bookingsaas.availability;

import com.bookingsaas.common.domain.Auditable;
import com.bookingsaas.common.domain.TimeRange;
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
 * A one-off hole in availability: a holiday, maintenance, a closure.
 *
 * <p>Scope comes from the resource: set, it removes that one resource (an
 * employee's day off, a machine being serviced — employees are resources too);
 * null, it closes the entire business.
 */
@Entity
@Table(name = "blocked_time")
@Getter
@Setter
@NoArgsConstructor
public class BlockedTime extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(length = 255)
    private String reason;

    public TimeRange range() {
        return TimeRange.of(startTime, endTime);
    }

    /** True when this block closes the business as a whole. */
    public boolean isBusinessWide() {
        return resourceId == null;
    }
}
