package com.bookingsaas.reservation;

/** How many upcoming bookings still hold a resource that has been deactivated. */
public record StrandedBookings(Long resourceId, long reservations) {
}
