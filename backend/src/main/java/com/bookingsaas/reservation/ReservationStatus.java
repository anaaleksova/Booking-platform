package com.bookingsaas.reservation;

import java.util.List;
import java.util.Set;

/** Lifecycle of a reservation. */
public enum ReservationStatus {

    /** Held but not yet confirmed. The MVP confirms immediately; it still holds its resources. */
    PENDING,
    CONFIRMED,
    /** Released: its resources become free again, the record is kept. */
    CANCELLED,
    COMPLETED;

    /**
     * Statuses whose reservations count toward revenue. Explicitly:
     * <pre>revenue = sum(final_price) where status in (CONFIRMED, COMPLETED)</pre>
     * PENDING is not yet a commitment and CANCELLED never happened.
     */
    public static final List<ReservationStatus> REVENUE = List.of(CONFIRMED, COMPLETED);

    public boolean occupiesTime() {
        return this != CANCELLED;
    }

    /** Still ahead of the customer: can be moved, confirmed or cancelled. */
    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }

    public boolean countsTowardRevenue() {
        return REVENUE.contains(this);
    }

    /** Which statuses an admin may move this one to. */
    public Set<ReservationStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> Set.of(COMPLETED, CANCELLED);
            case CANCELLED, COMPLETED -> Set.of();
        };
    }
}
