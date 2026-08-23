package com.bookingsaas.booking;

import java.math.BigDecimal;
import java.time.Instant;

/** One customer's spend, appointment count and most recent appointment, within a date range. */
public interface CustomerAggregateRow {
    Long getCustomerId();
    long getAppointmentCount();
    BigDecimal getTotalSpent();
    Instant getLastAppointmentAt();
}
