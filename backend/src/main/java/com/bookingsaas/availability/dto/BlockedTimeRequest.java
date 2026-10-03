package com.bookingsaas.availability.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Times are wall-clock in the <em>business</em> timezone — that is what the
 * owner typed into the form. The server converts to an instant; the browser's
 * own zone never enters the calculation.
 *
 * <p>{@code resourceId} null closes the whole business.
 */
public record BlockedTimeRequest(
        Long resourceId,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @Size(max = 255) String reason
) {
}
