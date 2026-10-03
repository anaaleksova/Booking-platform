package com.bookingsaas.reservation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The customer's view of their reservation.
 *
 * <p>Deliberately narrower than {@link ReservationResponse}: no internal notes,
 * no ids of other records. The reference is the only handle, and it is
 * unguessable.
 */
public record PublicReservationResponse(
        UUID reference,
        String businessName,
        String businessSlug,
        String businessAddress,
        String businessPhone,
        String serviceName,
        int numberOfParticipants,
        Instant startTime,
        Instant endTime,
        LocalDateTime localStartTime,
        LocalDateTime localEndTime,
        String timezone,
        String status,
        BigDecimal originalPrice,
        BigDecimal discountAmount,
        BigDecimal finalPrice,
        String currency,
        String discountCode,
        String customerName,
        List<Resource> resources,
        /* Whether the customer may still cancel it themselves. */
        boolean cancellable
) {

    public record Resource(String resourceTypeName, String name) {
    }
}
