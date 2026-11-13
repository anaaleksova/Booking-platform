package com.bookingsaas.notification;

import com.bookingsaas.business.Business;
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
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Reservation confirmed: one message to the customer, one to the business —
     * whatever the channel it came through (website, admin panel, Instagram...).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reservationConfirmed(ReservationNotificationPayload payload) {
        enqueue(NotificationType.RESERVATION_CONFIRMED_CUSTOMER, payload, payload.customerEmail(), null);
        enqueue(NotificationType.RESERVATION_CONFIRMED_BUSINESS, payload,
                businessService.notificationEmailOf(payload.businessId()), null);
        payload.staff().forEach(member -> enqueueForStaff(NotificationType.RESERVATION_CONFIRMED_STAFF, payload, member));
        scheduleReminder(payload);
    }

    /**
     * A booking request: the customer hears it was received and is not yet
     * confirmed, the owner that it waits for them. Staff and reminders wait
     * for the approval, which arrives as an ordinary confirmation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reservationRequested(ReservationNotificationPayload payload) {
        enqueue(NotificationType.RESERVATION_REQUESTED_CUSTOMER, payload, payload.customerEmail(), null);
        enqueue(NotificationType.RESERVATION_REQUESTED_BUSINESS, payload,
                businessService.notificationEmailOf(payload.businessId()), null);
    }

    /** A request turned down: only the customer is told; nobody else had it in their calendar. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reservationDeclined(ReservationNotificationPayload payload) {
        enqueue(NotificationType.RESERVATION_DECLINED_CUSTOMER, payload, payload.customerEmail(), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reservationCancelled(ReservationNotificationPayload payload) {
        enqueue(NotificationType.RESERVATION_CANCELLED_CUSTOMER, payload, payload.customerEmail(), null);
        enqueue(NotificationType.RESERVATION_CANCELLED_BUSINESS, payload,
                businessService.notificationEmailOf(payload.businessId()), null);
        payload.staff().forEach(member -> enqueueForStaff(NotificationType.RESERVATION_CANCELLED_STAFF, payload, member));
    }

    /**
     * Moved. Staff still on it hear it moved; staff newly on it get it as a new
     * appointment; staff taken off it hear it is no longer theirs.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reservationRescheduled(ReservationNotificationPayload payload, List<StaffMember> previousStaff) {
        enqueue(NotificationType.RESERVATION_RESCHEDULED_CUSTOMER, payload, payload.customerEmail(), null);
        // The owner hears too: the invitation in it moves the event in their calendar.
        enqueue(NotificationType.RESERVATION_RESCHEDULED_BUSINESS, payload,
                businessService.notificationEmailOf(payload.businessId()), null);
        Set<Long> before = employeeIds(previousStaff);
        for (StaffMember member : payload.staff()) {
            enqueueForStaff(before.contains(member.employeeId()) ? NotificationType.RESERVATION_RESCHEDULED_STAFF
                    : NotificationType.RESERVATION_CONFIRMED_STAFF, payload, member);
        }
        unassigned(payload, previousStaff);
        scheduleReminder(payload);
    }

    /** Same time, another employee: only the staff concerned hear about it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void staffChanged(ReservationNotificationPayload payload, List<StaffMember> previousStaff) {
        Set<Long> before = employeeIds(previousStaff);
        payload.staff().stream().filter(member -> !before.contains(member.employeeId()))
                .forEach(member -> enqueueForStaff(NotificationType.RESERVATION_CONFIRMED_STAFF, payload, member));
        unassigned(payload, previousStaff);
    }

    private void unassigned(ReservationNotificationPayload payload, List<StaffMember> previousStaff) {
        Set<Long> now = employeeIds(payload.staff());
        previousStaff.stream().filter(member -> !now.contains(member.employeeId()))
                .forEach(member -> enqueueForStaff(NotificationType.RESERVATION_UNASSIGNED_STAFF, payload, member));
    }

    private static Set<Long> employeeIds(List<StaffMember> staff) {
        return staff.stream().map(StaffMember::employeeId).collect(Collectors.toSet());
    }

    private void enqueueForStaff(NotificationType type, ReservationNotificationPayload payload, StaffMember member) {
        enqueue(type, payload, member.email(), null, member);
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
        enqueue(type, payload, recipient, dueAt, null);
    }

    /** @param staff the employee the message is for, when it is for one */
    private void enqueue(NotificationType type, ReservationNotificationPayload payload, String recipient, Instant dueAt,
                         StaffMember staff) {
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
        notification.setBody(composer.body(type, payload, staff));
        Business business = businessService.requireById(payload.businessId());
        notification.setHtmlBody(composer.html(type, payload,
                new NotificationComposer.Branding(business.getAccentColor(), business.isDarkTheme()), staff));
        notification.setCalendarIcs(composer.calendarFile(type, payload, recipient, clock.instant(), staff));
        notification.setSenderName(payload.businessName());
        notification.setReplyTo(payload.businessEmail());
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
