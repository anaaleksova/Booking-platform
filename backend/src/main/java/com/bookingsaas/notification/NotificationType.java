package com.bookingsaas.notification;

/** What happened. Values mirror the CHECK constraint on {@code notifications.type}. */
public enum NotificationType {

    RESERVATION_CONFIRMED_CUSTOMER,
    RESERVATION_CONFIRMED_BUSINESS,
    RESERVATION_CANCELLED_CUSTOMER,
    RESERVATION_CANCELLED_BUSINESS,
    RESERVATION_RESCHEDULED_CUSTOMER,
    RESERVATION_RESCHEDULED_BUSINESS,
    RESERVATION_REMINDER_CUSTOMER,
    /** To an assigned employee who can log in. */
    RESERVATION_CONFIRMED_STAFF,
    RESERVATION_RESCHEDULED_STAFF,
    RESERVATION_CANCELLED_STAFF,
    /** The reservation was given to someone else. */
    RESERVATION_UNASSIGNED_STAFF,
    /** A booking request, waiting for the owner's approval. */
    RESERVATION_REQUESTED_CUSTOMER,
    RESERVATION_REQUESTED_BUSINESS,
    /** The owner turned the request down. */
    RESERVATION_DECLINED_CUSTOMER;

    public boolean isForStaff() {
        return this == RESERVATION_CONFIRMED_STAFF || this == RESERVATION_RESCHEDULED_STAFF
                || this == RESERVATION_CANCELLED_STAFF || this == RESERVATION_UNASSIGNED_STAFF;
    }

    public boolean isReminder() {
        return this == RESERVATION_REMINDER_CUSTOMER;
    }
}
