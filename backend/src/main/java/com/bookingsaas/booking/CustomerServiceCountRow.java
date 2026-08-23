package com.bookingsaas.booking;

/** How many times one customer booked one service, within a date range — reduced to a favourite service. */
public interface CustomerServiceCountRow {
    Long getCustomerId();
    Long getServiceId();
    long getCount();
}
