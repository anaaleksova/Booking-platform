package com.bookingsaas.availability.spi;

import java.time.Instant;
import java.util.List;

/**
 * What the availability engine needs to know about existing bookings.
 *
 * <p>Declared here and implemented by the booking module: availability must not
 * depend on booking, or the two modules could never be separated. Dependency
 * inversion, in the plainest form.
 */
public interface BookedIntervalProvider {

    /**
     * Occupied windows overlapping {@code [from, to)} for a business.
     *
     * @param excludeBookingId a booking to ignore — a booking being rescheduled
     *                         must not be treated as a conflict with itself
     */
    List<OccupiedInterval> occupiedIntervals(Long businessId, Instant from, Instant to, Long excludeBookingId);
}
