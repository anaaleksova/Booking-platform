package com.bookingsaas.booking.dto;

import java.math.BigDecimal;
import java.util.List;

/** The dashboard summary cards. Kept small on purpose for the first version. */
public record DashboardStatsResponse(
        long todayBookings,
        long upcomingBookings,
        long completedBookings,
        long cancelledBookings,
        /** Value of today's non-cancelled bookings, at the price captured on each. */
        BigDecimal todayRevenue,
        String currency,
        List<BookingResponse> todaySchedule
) {
}
