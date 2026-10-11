package com.bookingsaas.integration.events;

import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.BusinessSettings;
import com.bookingsaas.integration.Integration;
import com.bookingsaas.integration.IntegrationProvider;
import com.bookingsaas.integration.IntegrationRepository;
import com.bookingsaas.notification.ReservationNotificationEvent;
import com.bookingsaas.notification.ReservationNotificationPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Turns reservation changes into outbox rows for the automation layer.
 *
 * <p>{@code BEFORE_COMMIT}: the row is written in the reservation's own
 * transaction, so an event exists if and only if the change does — nothing is
 * lost when n8n is down, and nothing is announced that was rolled back.
 * Delivery happens later, in {@link IntegrationEventDispatcher}.
 */
@Component
public class IntegrationEventListener {

    private static final Logger log = LoggerFactory.getLogger(IntegrationEventListener.class);

    private final IntegrationEventRepository repository;
    private final IntegrationRepository integrations;
    private final BusinessService businessService;
    private final Clock clock;

    public IntegrationEventListener(IntegrationEventRepository repository,
                                    IntegrationRepository integrations,
                                    BusinessService businessService,
                                    Clock clock) {
        this.repository = repository;
        this.integrations = integrations;
        this.businessService = businessService;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(ReservationNotificationEvent event) {
        ReservationNotificationPayload payload = event.payload();
        boolean connected = integrations.findByBusinessIdAndProvider(payload.businessId(), IntegrationProvider.N8N)
                .filter(Integration::isConnected)
                .isPresent();
        if (!connected) {
            return;
        }
        Instant now = clock.instant();
        switch (event.kind()) {
            case CREATED -> {
                enqueue(payload, IntegrationEventTypes.RESERVATION_CREATED, now);
                scheduleReminder(payload, now);
            }
            case CANCELLED -> {
                dropPendingReminders(payload.reservationId());
                enqueue(payload, IntegrationEventTypes.RESERVATION_CANCELLED, now);
            }
            case RESCHEDULED -> {
                dropPendingReminders(payload.reservationId());
                enqueue(payload, IntegrationEventTypes.RESERVATION_RESCHEDULED, now);
                scheduleReminder(payload, now);
            }
        }
    }

    private void scheduleReminder(ReservationNotificationPayload payload, Instant now) {
        BusinessSettings settings = businessService.settingsOf(payload.businessId());
        if (!settings.isReminderNotificationsEnabled()) {
            return;
        }
        Instant due = payload.start().minus(Duration.ofHours(settings.getReminderLeadHours()));
        if (due.isBefore(now)) {
            return;     // closer than the lead time: the confirmation is the only useful message
        }
        enqueue(payload, IntegrationEventTypes.RESERVATION_REMINDER_DUE, due);
    }

    private void dropPendingReminders(Long reservationId) {
        repository.findByReservationIdAndEventTypeAndStatus(reservationId,
                        IntegrationEventTypes.RESERVATION_REMINDER_DUE, IntegrationEvent.Status.PENDING)
                .forEach(reminder -> {
                    reminder.markSkipped("Reservation changed");
                    // Flushed before the replacement is inserted: Hibernate
                    // would otherwise insert first and trip the unique index.
                    repository.saveAndFlush(reminder);
                });
    }

    private void enqueue(ReservationNotificationPayload payload, String type, Instant due) {
        IntegrationEvent event = new IntegrationEvent();
        event.setBusinessId(payload.businessId());
        event.setReservationId(payload.reservationId());
        event.setEventType(type);
        event.setDueAt(due);
        // Flushed now so the partial unique index on pending reminders sees it
        // before a second reminder for the same reservation could be added.
        repository.saveAndFlush(event);
        log.debug("Queued {} for reservation {} due {}", type, payload.reservationId(), due);
    }
}
