package com.bookingsaas.reservation;

import com.bookingsaas.reservation.dto.ResourceSelection;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A reservation requested through the integration API. Like the customer
 * request it carries no price: the price and discount are computed by the
 * server.
 *
 * @param startTime wall-clock in the business timezone, as returned by availability
 * @param source    the channel the customer used
 */
public record IntegrationReservationCommand(
        Long serviceId,
        int participants,
        LocalDateTime startTime,
        List<ResourceSelection> resourceSelections,
        String discountCode,
        String notes,
        ReservationSource source
) {
}
