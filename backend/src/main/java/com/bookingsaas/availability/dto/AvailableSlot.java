package com.bookingsaas.availability.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/**
 * One bookable start time.
 *
 * <p>The candidate staff and resources travel with the slot so that "any
 * available" can be resolved without a second round trip — although the final
 * choice is always re-made inside the booking transaction.
 */
public record AvailableSlot(
        LocalTime time,
        Instant startDateTime,
        Instant endDateTime,
        List<Long> availableStaffIds,
        List<Long> availableResourceIds
) {
}
