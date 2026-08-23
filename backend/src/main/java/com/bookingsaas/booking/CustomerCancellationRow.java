package com.bookingsaas.booking;

/** How many bookings one customer cancelled, within a date range. */
public interface CustomerCancellationRow {
    Long getCustomerId();
    long getCancellationCount();
}
