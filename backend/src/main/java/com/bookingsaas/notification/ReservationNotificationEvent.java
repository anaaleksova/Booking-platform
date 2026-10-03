package com.bookingsaas.notification;

/**
 * Published by the reservation module after a reservation changes.
 *
 * <p>Handled after the transaction commits, so a rolled-back reservation never
 * produces a "your appointment is confirmed" email.
 */
public record ReservationNotificationEvent(Kind kind, ReservationNotificationPayload payload) {

    public enum Kind {
        CREATED,
        CANCELLED,
        RESCHEDULED
    }

    public static ReservationNotificationEvent created(ReservationNotificationPayload payload) {
        return new ReservationNotificationEvent(Kind.CREATED, payload);
    }

    public static ReservationNotificationEvent cancelled(ReservationNotificationPayload payload) {
        return new ReservationNotificationEvent(Kind.CANCELLED, payload);
    }

    public static ReservationNotificationEvent rescheduled(ReservationNotificationPayload payload) {
        return new ReservationNotificationEvent(Kind.RESCHEDULED, payload);
    }
}
