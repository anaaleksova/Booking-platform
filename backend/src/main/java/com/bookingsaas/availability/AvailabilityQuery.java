package com.bookingsaas.availability;

import java.time.LocalDate;

/**
 * One availability question.
 *
 * @param staffId          restrict to a chosen staff member; null means "any available"
 * @param resourceId       restrict to a chosen resource; null means "any available"
 * @param excludeBookingId ignored when detecting conflicts — set while rescheduling
 *                         a booking so it does not block its own new time
 */
public record AvailabilityQuery(
        Long serviceId,
        LocalDate from,
        LocalDate to,
        Long staffId,
        Long resourceId,
        Long excludeBookingId
) {

    public static AvailabilityQuery of(Long serviceId, LocalDate from, LocalDate to, Long staffId, Long resourceId) {
        return new AvailabilityQuery(serviceId, from, to, staffId, resourceId, null);
    }
}
