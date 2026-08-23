package com.bookingsaas.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A customer's record with one business: the numbers a receptionist wants at a
 * glance, plus the appointments behind them.
 */
public record CustomerHistoryResponse(
        Long customerId,
        String customerName,
        long totalAppointments,
        long completedAppointments,
        long cancelledAppointments,
        long noShowAppointments,
        long upcomingAppointments,
        Instant lastAppointmentAt,
        Instant nextAppointmentAt,
        /** Value of the non-cancelled bookings below. */
        BigDecimal totalSpent,
        BigDecimal averageBookingValue,
        Long favouriteServiceId,
        String favouriteServiceName,
        List<BookingResponse> bookings
) {
}
