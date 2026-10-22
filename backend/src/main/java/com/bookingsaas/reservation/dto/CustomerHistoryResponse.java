package com.bookingsaas.reservation.dto;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A customer's record with the business: headline numbers over the whole
 * history, plus one page of reservations, newest first.
 */
public record CustomerHistoryResponse(
        Long customerId,
        String customerName,
        long totalReservations,
        long completedReservations,
        long cancelledReservations,
        long noShowReservations,
        long upcomingReservations,
        Instant lastVisitAt,
        Instant nextReservationAt,
        /* Sum of final prices of CONFIRMED and COMPLETED reservations. */
        BigDecimal totalSpent,
        BigDecimal averageReservationValue,
        String favouriteServiceName,
        Page<ReservationResponse> reservations
) {
}
