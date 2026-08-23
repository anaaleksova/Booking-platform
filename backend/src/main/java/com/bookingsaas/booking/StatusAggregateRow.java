package com.bookingsaas.booking;

import java.math.BigDecimal;

/** Appointment count and revenue for one {@link BookingStatus}, within a date range. */
public interface StatusAggregateRow {
    BookingStatus getStatus();
    long getCount();
    BigDecimal getRevenue();
}
