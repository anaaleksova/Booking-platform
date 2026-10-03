package com.bookingsaas.reservation.dto;

import com.bookingsaas.reservation.ReservationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code PATCH /api/admin/reservations/{id}}: confirm, complete or cancel. */
public record StatusChangeRequest(
        @NotNull ReservationStatus status,
        /* Only used when cancelling. */
        @Size(max = 255) String reason
) {
}
