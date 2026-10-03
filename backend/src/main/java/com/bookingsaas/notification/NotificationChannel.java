package com.bookingsaas.notification;

/**
 * Delivery channels. Only {@link #EMAIL} has a sender in the MVP; the others
 * exist so adding SMS or WhatsApp later is a new {@code NotificationSender}
 * bean rather than a change to reservation logic.
 */
public enum NotificationChannel {
    EMAIL,
    SMS,
    WHATSAPP,
    VIBER
}
