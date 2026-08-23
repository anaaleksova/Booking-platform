package com.bookingsaas.notification;

/** What happened. Values mirror the CHECK constraint on {@code notifications.type}. */
public enum NotificationType {

    BOOKING_CONFIRMED_CUSTOMER,
    BOOKING_CONFIRMED_BUSINESS,
    BOOKING_CANCELLED_CUSTOMER,
    BOOKING_CANCELLED_BUSINESS,
    BOOKING_RESCHEDULED_CUSTOMER,
    BOOKING_REMINDER_CUSTOMER;

    public boolean isReminder() {
        return this == BOOKING_REMINDER_CUSTOMER;
    }
}
