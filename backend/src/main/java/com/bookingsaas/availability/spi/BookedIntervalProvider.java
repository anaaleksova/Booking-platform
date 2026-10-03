package com.bookingsaas.availability.spi;

import java.time.Instant;
import java.util.List;

/**
 * What the availability engine needs to know about existing reservations.
 *
 * <p>Declared here and implemented by the reservation module: availability must
 * not depend on reservations, or the two modules could never be separated.
 * Dependency inversion, in the plainest form.
 */
public interface BookedIntervalProvider {

    /**
     * Resource occupancy overlapping {@code [from, to)} for a business.
     *
     * @param excludeReservationId a reservation to ignore — one being moved
     *                             must not be treated as a conflict with itself
     */
    List<OccupiedInterval> occupiedIntervals(Long businessId, Instant from, Instant to, Long excludeReservationId);
}
