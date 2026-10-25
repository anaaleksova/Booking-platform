package com.bookingsaas.event.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * An event as customers see it on the booking page.
 *
 * @param serviceId what to book for it; null when it is an announcement only
 * @param booking   OPEN (tables left), FULL (fully booked) or NONE (nothing to
 *                  book: no service, or it has already started)
 */
public record PublicEventResponse(
        Long id,
        String title,
        String description,
        String performer,
        String imageUrl,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Long serviceId,
        String booking
) {
}
