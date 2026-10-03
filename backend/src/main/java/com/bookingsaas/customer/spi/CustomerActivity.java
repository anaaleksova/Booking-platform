package com.bookingsaas.customer.spi;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param reservationCount reservations that were not cancelled
 * @param totalSpent       sum of final prices of CONFIRMED and COMPLETED
 *                         reservations — the same definition revenue uses
 * @param lastReservationAt start of the most recent non-cancelled reservation
 *                         that has already begun
 */
public record CustomerActivity(long reservationCount, BigDecimal totalSpent, Instant lastReservationAt) {

    public static final CustomerActivity NONE = new CustomerActivity(0, BigDecimal.ZERO, null);
}
