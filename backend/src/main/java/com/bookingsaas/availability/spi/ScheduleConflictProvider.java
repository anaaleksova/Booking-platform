package com.bookingsaas.availability.spi;

import java.time.Instant;
import java.util.List;

/**
 * Finds the bookings a schedule change collides with. Declared here and
 * implemented by the reservation module, like {@link BookedIntervalProvider}.
 * Only active bookings that have not ended yet count: history is not "in the way".
 */
public interface ScheduleConflictProvider {

    /** Bookings holding the resource at any moment of {@code [from, to)}, buffers included. */
    List<ScheduleConflict> holding(Long businessId, Long resourceId, Instant from, Instant to);

    /** Bookings whose appointment touches {@code [from, to)}, whatever they hold. */
    List<ScheduleConflict> during(Long businessId, Instant from, Instant to);
}
