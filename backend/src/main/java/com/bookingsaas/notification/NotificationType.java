package com.bookingsaas.notification;

/** What happened. Values mirror the CHECK constraint on {@code notifications.type}. */
public enum NotificationType {

    RESERVATION_CONFIRMED_CUSTOMER,
    RESERVATION_CONFIRMED_BUSINESS,
    RESERVATION_CANCELLED_CUSTOMER,
    RESERVATION_CANCELLED_BUSINESS,
    RESERVATION_RESCHEDULED_CUSTOMER,
    RESERVATION_REMINDER_CUSTOMER;

    public boolean isReminder() {
        return this == RESERVATION_REMINDER_CUSTOMER;
    }
}
