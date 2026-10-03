package com.bookingsaas.reservation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A customer's record with the business: headline numbers plus every reservation. */
public record CustomerHistoryResponse(
        Long customerId,
        String customerName,
        long totalReservations,
        long completedReservations,
        long cancelledReservations,
        long upcomingReservations,
        Instant lastVisitAt,
        Instant nextReservationAt,
        /* Sum of final prices of CONFIRMED and COMPLETED reservations. */
        BigDecimal totalSpent,
        BigDecimal averageReservationValue,
        String favouriteServiceName,
        List<ReservationResponse> reservations
) {
}
