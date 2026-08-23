package com.bookingsaas.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The confirmation page's view of a booking.
 *
 * <p>A deliberately narrower shape than {@link BookingResponse}: no internal
 * notes, no customer id, nothing about the business's other bookings. The
 * reference is the only handle, and it is unguessable.
 */
public record PublicBookingResponse(
        UUID reference,
        String businessName,
        String businessSlug,
        String businessAddress,
        String businessPhone,
        String serviceName,
        String staffName,
        Instant startDateTime,
        Instant endDateTime,
        LocalDateTime localStartDateTime,
        LocalDateTime localEndDateTime,
        String timezone,
        String status,
        BigDecimal price,
        String currency,
        String customerName,
        /** Whether the customer may still cancel this booking themselves. */
        boolean cancellable
) {
}
