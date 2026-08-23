package com.bookingsaas.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * A new time, and optionally a different staff member or resource. The move is
 * validated exactly like a new booking, minus the booking's own current slot.
 */
public record RescheduleBookingRequest(
        @NotNull LocalDateTime startDateTime,
        Long staffId,
        Long resourceId
) {
}
