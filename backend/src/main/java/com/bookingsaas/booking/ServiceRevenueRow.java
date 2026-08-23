package com.bookingsaas.booking;

import java.math.BigDecimal;

/** Appointment count and revenue for one service, within a date range. */
public interface ServiceRevenueRow {
    Long getServiceId();
    long getAppointmentCount();
    BigDecimal getRevenue();
    String getCurrency();
}
