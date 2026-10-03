package com.bookingsaas.notification;

import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.BusinessSettings;
import com.bookingsaas.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The module's front door: queue a message, do not send one.
 *
 * <p>Callers never learn whether email, SMS or anything else is configured —
 * they describe what happened and the outbox takes over.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final NotificationComposer composer;
    private final BusinessService businessService;
    private final AppProperties properties;
    private final Clock clock;

    public NotificationService(NotificationRepository repository,
                               NotificationComposer composer,
                               BusinessService businessService,
                               AppProperties properties,
                               Clock clock) {
        this.repository = repository;
        this.composer = composer;
        this.businessService = businessService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Reservation confirmed: one message to the customer, one to the business. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reservationConfirmed(ReservationNotificationPayload payload) {
        enqueue(NotificationType.RESERVATION_CONFIRMED_CUSTOMER, payload, payload.customerEmail(), null);
        enqueue(NotificationType.RESERVATION_CONFIRMED_BUSINESS, payload, payload.businessEmail(), null);
        scheduleReminder(payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reservationCancelled(ReservationNotificationPayload payload) {
        enqueue(NotificationType.RESERVATION_CANCELLED_CUSTOMER, payload, payload.customerEmail(), null);
        enqueue(NotificationType.RESERVATION_CANCELLED_BUSINESS, payload, payload.businessEmail(), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reservationRescheduled(ReservationNotificationPayload payload) {
        enqueue(NotificationType.RESERVATION_RESCHEDULED_CUSTOMER, payload, payload.customerEmail(), null);
        scheduleReminder(payload);
    }

    /**
     * Queues the reminder at reservation time rather than scanning for it later.
     * The unique index on {@code (reservation_id, type)} makes a repeat harmless,
     * so a reschedule can simply try again.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleReminder(ReservationNotificationPayload payload) {
        BusinessSettings settings = businessService.settingsOf(payload.businessId());
        if (!settings.isReminderNotificationsEnabled()) {
            return;
        }
        Instant dueAt = payload.start().minus(Duration.ofHours(settings.getReminderLeadHours()));
        if (dueAt.isBefore(clock.instant())) {
            // The appointment is nearer than the reminder lead time: the
            // confirmation just sent is the only useful message.
            return;
        }
        enqueue(NotificationType.RESERVATION_REMINDER_CUSTOMER, payload, payload.customerEmail(), dueAt);
    }

    /** Drops any reminder still waiting for a reservation that no longer stands. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelPendingReminders(Long reservationId) {
        repository.findByReservationIdOrderByCreatedAtDesc(reservationId).stream()
                .filter(notification -> notification.getType().isReminder())
                .filter(notification -> notification.getStatus() == NotificationStatus.PENDING)
                .forEach(notification -> {
                    notification.markSkipped("Reservation is no longer active");
                    repository.save(notification);
                });
    }

    private void enqueue(NotificationType type, ReservationNotificationPayload payload, String recipient, Instant dueAt) {
        if (!properties.notifications().enabled()) {
            return;
        }
        if (recipient == null || recipient.isBlank()) {
            return;                                   // nothing to send to
        }
        BusinessSettings settings = businessService.settingsOf(payload.businessId());
        if (!settings.isEmailNotificationsEnabled()) {
            return;
        }

        Notification notification = new Notification();
        notification.setBusinessId(payload.businessId());
        notification.setReservationId(payload.reservationId());
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setType(type);
        notification.setRecipient(recipient);
        notification.setSubject(composer.subject(type, payload));
        notification.setBody(composer.body(type, payload));
        notification.setScheduledAt(dueAt == null ? clock.instant() : dueAt);

        try {
            repository.save(notification);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            // Only the reminder has a uniqueness rule; a duplicate simply means
            // one is already queued.
            log.debug("Notification {} for reservation {} already queued", type, payload.reservationId());
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> forReservation(Long reservationId) {
        return repository.findByReservationIdOrderByCreatedAtDesc(reservationId);
    }
}
