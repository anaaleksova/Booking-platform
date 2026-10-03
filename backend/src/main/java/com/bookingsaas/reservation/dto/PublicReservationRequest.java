package com.bookingsaas.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * What a customer submits. No price field on purpose: the price and discount
 * are always computed by the server.
 *
 * @param startTime          wall-clock in the business timezone, as shown on the slot
 * @param resourceSelections only for requirements the customer may choose;
 *                           everything else is assigned by the server
 */
public record PublicReservationRequest(
        @NotNull Long serviceId,
        @NotNull @Min(1) Integer participants,
        @NotNull LocalDateTime startTime,
        @Size(max = 20) List<@Valid ResourceSelection> resourceSelections,
        @Size(max = 40) String discountCode,
        @NotNull @Valid CustomerDetails customer,
        @Size(max = 2000) String notes
) {
}
