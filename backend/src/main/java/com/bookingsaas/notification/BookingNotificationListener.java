package com.bookingsaas.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges booking events into the outbox.
 *
 * <p>{@code AFTER_COMMIT} guarantees a message is only ever queued for a
 * booking that really exists, and {@code @Async} keeps queuing off the request
 * thread — a slow database write here must not lengthen the customer's booking
 * request.
 */
@Component
public class BookingNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationListener.class);

    private final NotificationService notificationService;

    public BookingNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(BookingNotificationEvent event) {
        try {
            switch (event.kind()) {
                case CREATED -> notificationService.bookingConfirmed(event.payload());
                case CANCELLED -> {
                    notificationService.cancelPendingReminders(event.payload().bookingId());
                    notificationService.bookingCancelled(event.payload());
                }
                case RESCHEDULED -> {
                    notificationService.cancelPendingReminders(event.payload().bookingId());
                    notificationService.bookingRescheduled(event.payload());
                }
            }
        } catch (RuntimeException ex) {
            // A notification failure must never surface as a booking failure.
            log.error("Could not queue notifications for booking {}", event.payload().bookingId(), ex);
        }
    }
}
