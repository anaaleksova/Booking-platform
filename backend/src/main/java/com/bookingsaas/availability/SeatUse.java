package com.bookingsaas.availability;

import com.bookingsaas.common.domain.TimeRange;

/** Seats of a shared resource held by one booking during its block window. */
public record SeatUse(TimeRange range, int seats) {
}
