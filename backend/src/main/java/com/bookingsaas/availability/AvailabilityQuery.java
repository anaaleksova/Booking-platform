package com.bookingsaas.availability;

import java.time.LocalDate;

/**
 * One availability question.
 *
 * @param participants         group size; checked against the service maximum
 *                             and against resource capacity
 * @param excludeReservationId ignored when detecting conflicts — set while
 *                             moving a reservation so it does not block itself
 */
public record AvailabilityQuery(
        Long serviceId,
        LocalDate from,
        LocalDate to,
        int participants,
        Long excludeReservationId
) {
}
