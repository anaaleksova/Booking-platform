package com.bookingsaas.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A booking as the dashboard sees it.
 *
 * <p>Instants are the truth; the {@code local*} fields are the same moment
 * already rendered in the business timezone, so the calendar does not have to
 * carry timezone maths of its own.
 */
public record BookingResponse(
        Long id,
        Long serviceId,
        String serviceName,
        Long staffId,
        String staffName,
        Long resourceId,
        String resourceName,
        Long customerId,
        String customerName,
        String customerPhone,
        String customerEmail,
        Instant startDateTime,
        Instant endDateTime,
        LocalDateTime localStartDateTime,
        LocalDateTime localEndDateTime,
        String timezone,
        String status,
        String source,
        String customerNotes,
        String internalNotes,
        BigDecimal price,
        String currency,
        UUID publicReference,
        Instant createdAt
) {
}
