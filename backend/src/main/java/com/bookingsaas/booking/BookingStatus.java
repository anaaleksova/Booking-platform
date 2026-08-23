package com.bookingsaas.booking;

import java.util.List;

/** Lifecycle of a booking. */
public enum BookingStatus {

    /** Reserved but not yet confirmed. Unused by the MVP flow; it still holds its slot. */
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    /** The customer did not turn up. The slot is released, the record is kept. */
    NO_SHOW;

    /**
     * Statuses that still hold their slot. Must stay in step with the WHERE
     * clause of the exclusion constraints in {@code V4}: the database and the
     * engine have to agree on what "taken" means.
     */
    public static final List<BookingStatus> OCCUPYING = List.of(PENDING, CONFIRMED, COMPLETED);

    public boolean occupiesTime() {
        return OCCUPYING.contains(this);
    }

    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }
}
