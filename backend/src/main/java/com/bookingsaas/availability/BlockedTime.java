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
 * A one-off hole in availability: vacation, a meeting, maintenance, a public
 * holiday.
 *
 * <p>Scope comes from which id is set — staff only, resource only, or (both
 * null) the entire business. One shape covers "Ana is on holiday", "Reformer 2
 * is being serviced" and "closed on 1 January".
 */
@Entity
@Table(name = "blocked_times")
@Getter
@Setter
@NoArgsConstructor
public class BlockedTime extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "start_date_time", nullable = false)
    private Instant startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private Instant endDateTime;

    @Column(length = 255)
    private String reason;

    public TimeRange range() {
        return TimeRange.of(startDateTime, endDateTime);
    }

    /** True when this block closes the business as a whole. */
    public boolean isBusinessWide() {
        return staffId == null && resourceId == null;
    }
}
