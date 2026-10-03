package com.bookingsaas.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Moves a reservation and/or changes its resources. Validated exactly like a
 * new reservation, minus the reservation's own current hold.
 *
 * @param resourceSelections null keeps the current resources when they are
 *                           still free at the new time, otherwise reassigns
 */
public record RescheduleRequest(
        @NotNull LocalDateTime startTime,
        @Size(max = 20) List<@Valid ResourceSelection> resourceSelections
) {
}
