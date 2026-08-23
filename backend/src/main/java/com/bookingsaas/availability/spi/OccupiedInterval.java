package com.bookingsaas.availability.spi;

import com.bookingsaas.common.domain.TimeRange;

/**
 * A window in which a staff member or a resource is already taken.
 *
 * <p>The range is the <em>blocked</em> window — the appointment plus its
 * buffers — not the window the customer sees, so buffer rules are enforced by
 * the same overlap test as everything else.
 */
public record OccupiedInterval(Long bookingId, Long staffId, Long resourceId, TimeRange range) {
}
