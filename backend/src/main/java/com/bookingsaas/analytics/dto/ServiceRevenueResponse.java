package com.bookingsaas.analytics.dto;

import java.math.BigDecimal;

/** One row of the "what makes the most money" report. */
public record ServiceRevenueResponse(
        Long serviceId,
        String serviceName,
        boolean active,
        long appointmentCount,
        BigDecimal revenue,
        String currency
) {
}
