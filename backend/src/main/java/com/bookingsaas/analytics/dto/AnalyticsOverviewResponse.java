package com.bookingsaas.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The reports overview: the four always-on revenue totals, plus appointment
 * counts and rates for the selected {@code rangeFrom}/{@code rangeTo} window.
 */
public record AnalyticsOverviewResponse(
        BigDecimal revenueToday,
        BigDecimal revenueThisWeek,
        BigDecimal revenueThisMonth,
        BigDecimal revenueThisYear,
        String currency,
        LocalDate rangeFrom,
        LocalDate rangeTo,
        long appointmentCount,
        long completedCount,
        long cancelledCount,
        long noShowCount,
        BigDecimal averageBookingValue,
        double cancellationRate,
        double noShowRate
) {
}
