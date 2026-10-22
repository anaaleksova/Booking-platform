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
    COMPLETED,
    /**
     * The customer did not turn up. Like a cancellation it releases the
     * resources (a walk-in can have the rest of the slot) and earns nothing;
     * unlike one it is the business's record that the customer did not come.
     */
    NO_SHOW;

    /**
     * Statuses whose reservations count toward revenue. Explicitly:
     * <pre>revenue = sum(final_price) where status in (CONFIRMED, COMPLETED)</pre>
     * PENDING is not yet a commitment and CANCELLED never happened.
     */
    public static final List<ReservationStatus> REVENUE = List.of(CONFIRMED, COMPLETED);

    public boolean occupiesTime() {
        return this != CANCELLED && this != NO_SHOW;
    }

    /** Booked and kept (or still ahead): what visit counts and "last visit" look at. */
    public boolean wasKept() {
        return this != CANCELLED && this != NO_SHOW;
    }

    /** Still ahead of the customer: can be moved, confirmed or cancelled. */
    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }

    public boolean countsTowardRevenue() {
        return REVENUE.contains(this);
    }

    /**
     * Which statuses an admin may move this one to. COMPLETED and NO_SHOW can
     * be swapped for each other, to correct the wrong button pressed.
     */
    public Set<ReservationStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> Set.of(CONFIRMED, CANCELLED, NO_SHOW);
            case CONFIRMED -> Set.of(COMPLETED, CANCELLED, NO_SHOW);
            case COMPLETED -> Set.of(NO_SHOW);
            case NO_SHOW -> Set.of(COMPLETED);
            case CANCELLED -> Set.of();
        };
    }
}
