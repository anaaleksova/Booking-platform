package com.bookingsaas.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the customer analytics table, ranked by total spend. */
public record CustomerAnalyticsResponse(
        Long customerId,
        String customerName,
        long appointmentCount,
        BigDecimal totalSpent,
        BigDecimal averageAppointmentValue,
        Instant lastAppointmentAt,
        Long favouriteServiceId,
        String favouriteServiceName,
        long cancellationCount
) {
}
