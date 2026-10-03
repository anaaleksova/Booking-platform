package com.bookingsaas.reservation;

import com.bookingsaas.availability.spi.BookedIntervalProvider;
import com.bookingsaas.availability.spi.OccupiedInterval;
import com.bookingsaas.common.domain.TimeRange;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The reservation module's implementation of what availability needs to know.
 *
 * <p>The dependency points this way — reservations know about availability,
 * never the reverse — so the engine can be reasoned about (and tested) without
 * any reservation persistence at all.
 */
@Component
public class ReservationIntervalProvider implements BookedIntervalProvider {

    /** No reservation id is ever 0, so this excludes nothing. */
    private static final long EXCLUDE_NOTHING = 0L;

    private final ReservationResourceRepository repository;

    public ReservationIntervalProvider(ReservationResourceRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccupiedInterval> occupiedIntervals(Long businessId, Instant from, Instant to,
                                                    Long excludeReservationId) {
        return repository.findOccupying(businessId, from, to,
                        excludeReservationId == null ? EXCLUDE_NOTHING : excludeReservationId).stream()
                .map(held -> new OccupiedInterval(held.getReservation().getId(), held.getResourceId(),
                        TimeRange.of(held.getBlockStartTime(), held.getBlockEndTime()), held.getSeats()))
                .toList();
    }
}
