package com.bookingsaas.availability.spi;

import com.bookingsaas.common.domain.TimeRange;

/**
 * A window in which a resource is already held by a reservation.
 *
 * <p>The range is the <em>blocked</em> window — the appointment plus its
 * buffers — not the window the customer sees, so buffer rules are enforced by
 * the same overlap test as everything else.
 *
 * @param seats seats held on a shared resource; {@code null} when the
 *              reservation holds the whole resource
 */
public record OccupiedInterval(Long reservationId, Long resourceId, TimeRange range, Integer seats) {
}
