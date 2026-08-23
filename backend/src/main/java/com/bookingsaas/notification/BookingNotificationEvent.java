package com.bookingsaas.notification;

/**
 * Published by the booking module after a booking changes.
 *
 * <p>Handled after the transaction commits, so a rolled-back booking never
 * produces a "your appointment is confirmed" email.
 */
public record BookingNotificationEvent(Kind kind, BookingNotificationPayload payload) {

    public enum Kind {
        CREATED,
        CANCELLED,
        RESCHEDULED
    }

    public static BookingNotificationEvent created(BookingNotificationPayload payload) {
        return new BookingNotificationEvent(Kind.CREATED, payload);
    }

    public static BookingNotificationEvent cancelled(BookingNotificationPayload payload) {
        return new BookingNotificationEvent(Kind.CANCELLED, payload);
    }

    public static BookingNotificationEvent rescheduled(BookingNotificationPayload payload) {
        return new BookingNotificationEvent(Kind.RESCHEDULED, payload);
    }
}
