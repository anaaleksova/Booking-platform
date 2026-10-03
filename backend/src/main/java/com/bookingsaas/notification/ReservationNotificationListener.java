package com.bookingsaas.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges reservation events into the outbox.
 *
 * <p>{@code AFTER_COMMIT} guarantees a message is only ever queued for a
 * reservation that really exists, and {@code @Async} keeps queuing off the request
 * thread — a slow database write here must not lengthen the customer's reservation
 * request.
 */
@Component
public class ReservationNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationNotificationListener.class);

    private final NotificationService notificationService;

    public ReservationNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReservationNotificationEvent event) {
        try {
            switch (event.kind()) {
                case CREATED -> notificationService.reservationConfirmed(event.payload());
                case CANCELLED -> {
                    notificationService.cancelPendingReminders(event.payload().reservationId());
                    notificationService.reservationCancelled(event.payload());
                }
                case RESCHEDULED -> {
                    notificationService.cancelPendingReminders(event.payload().reservationId());
                    notificationService.reservationRescheduled(event.payload());
                }
            }
        } catch (RuntimeException ex) {
            // A notification failure must never surface as a reservation failure.
            log.error("Could not queue notifications for reservation {}", event.payload().reservationId(), ex);
        }
    }
}
