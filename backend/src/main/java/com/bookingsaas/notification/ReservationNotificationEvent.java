package com.bookingsaas.notification;

import java.util.List;

/**
 * Published by the reservation module after a reservation changes.
 *
 * <p>Handled after the transaction commits, so a rolled-back reservation never
 * produces a "your appointment is confirmed" email.
 *
 * @param previousStaff for a move or reassignment: who was assigned before, so
 *                      someone taken off it hears that it is no longer theirs
 */
public record ReservationNotificationEvent(Kind kind, ReservationNotificationPayload payload,
                                           List<StaffMember> previousStaff) {

    public enum Kind {
        CREATED,
        CANCELLED,
        RESCHEDULED,
        /** Same time, different employee: only the staff concerned hear about it. */
        STAFF_CHANGED
    }

    public ReservationNotificationEvent {
        previousStaff = previousStaff == null ? List.of() : List.copyOf(previousStaff);
    }

    public static ReservationNotificationEvent created(ReservationNotificationPayload payload) {
        return new ReservationNotificationEvent(Kind.CREATED, payload, null);
    }

    public static ReservationNotificationEvent cancelled(ReservationNotificationPayload payload) {
        return new ReservationNotificationEvent(Kind.CANCELLED, payload, null);
    }

    public static ReservationNotificationEvent rescheduled(ReservationNotificationPayload payload,
                                                           List<StaffMember> previousStaff) {
        return new ReservationNotificationEvent(Kind.RESCHEDULED, payload, previousStaff);
    }

    public static ReservationNotificationEvent staffChanged(ReservationNotificationPayload payload,
                                                            List<StaffMember> previousStaff) {
        return new ReservationNotificationEvent(Kind.STAFF_CHANGED, payload, previousStaff);
    }
}
