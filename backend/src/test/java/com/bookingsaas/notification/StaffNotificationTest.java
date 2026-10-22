package com.bookingsaas.notification;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.BusinessSettings;
import com.bookingsaas.common.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The employees assigned to a reservation hear about it, with the appointment in their own calendar. */
class StaffNotificationTest {

    private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
    private static final AppProperties PROPERTIES = new AppProperties(null, null, "https://book.example.com",
            new AppProperties.Notifications("bookings@example.com", true, 5000, 60000));
    private static final StaffMember ELENA = new StaffMember(11L, "Elena", "elena@spa.test");
    private static final StaffMember ANNA = new StaffMember(12L, "Anna", "anna@spa.test");

    private final NotificationComposer composer = new NotificationComposer(PROPERTIES);
    private NotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        BusinessService businessService = mock(BusinessService.class);
        BusinessSettings settings = new BusinessSettings(1L);
        settings.setReminderNotificationsEnabled(false);
        when(businessService.settingsOf(1L)).thenReturn(settings);
        when(businessService.notificationEmailOf(1L)).thenReturn("owner@spa.test");
        when(businessService.requireById(1L)).thenReturn(new Business());
        service = new NotificationService(repository, composer, businessService, PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ReservationNotificationPayload payload(List<StaffMember> staff) {
        return new ReservationNotificationPayload(1L, 7L, "Serenity Spa", "hello@spa.test", "+389 2 312 4567",
                "Main street 1", "Europe/Skopje", "Ana Petrova", "ana@example.com", "+38970555666", "WEB",
                "Classic Massage", 1, "Room 1, Elena", Instant.parse("2026-10-21T12:00:00Z"),
                Instant.parse("2026-10-21T13:00:00Z"), "3f1c9a52-8a8e-4a43-9a1e-1b2f0c7d9e11", null, staff);
    }

    /** What was queued, by recipient, as "TYPE" (only the staff messages). */
    private Map<String, Notification> staffMessages() {
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, atLeastOnce()).save(saved.capture());
        return saved.getAllValues().stream().filter(n -> n.getType().isForStaff())
                .collect(Collectors.toMap(Notification::getRecipient, n -> n));
    }

    @Test
    void the_assigned_employee_gets_a_new_appointment_email_with_an_invitation() {
        service.reservationConfirmed(payload(List.of(ELENA)));

        Notification email = staffMessages().get("elena@spa.test");
        assertThat(email.getType()).isEqualTo(NotificationType.RESERVATION_CONFIRMED_STAFF);
        assertThat(email.getSubject()).isEqualTo("New appointment: Classic Massage — Ana Petrova, Wed 21 Oct at 14:00");
        assertThat(email.getBody()).startsWith("Hello Elena,").contains("You have a new appointment.",
                "+38970555666", "https://book.example.com/admin/reservations");
        assertThat(email.getHtmlBody()).contains("Hello Elena,", "Open my reservations");
        assertThat(email.getCalendarIcs().replace("\r\n ", "")).contains("METHOD:REQUEST",
                "UID:3f1c9a52-8a8e-4a43-9a1e-1b2f0c7d9e11-staff-11@booking-platform", "mailto:elena@spa.test");
    }

    @Test
    void an_employee_without_a_login_or_nobody_assigned_means_no_staff_email() {
        service.reservationConfirmed(payload(List.of()));
        assertThat(staffMessages()).isEmpty();
    }

    @Test
    void a_cancellation_removes_the_appointment_from_the_employees_calendar() {
        service.reservationCancelled(payload(List.of(ELENA)));

        Notification email = staffMessages().get("elena@spa.test");
        assertThat(email.getType()).isEqualTo(NotificationType.RESERVATION_CANCELLED_STAFF);
        assertThat(email.getCalendarIcs()).contains("METHOD:CANCEL", "STATUS:CANCELLED");
    }

    @Test
    void moving_and_reassigning_tells_each_employee_what_changed_for_them() {
        // Was Elena's; now Anna's, at another time.
        service.reservationRescheduled(payload(List.of(ANNA)), List.of(ELENA));

        Map<String, Notification> messages = staffMessages();
        assertThat(messages.get("anna@spa.test").getType()).isEqualTo(NotificationType.RESERVATION_CONFIRMED_STAFF);
        Notification elena = messages.get("elena@spa.test");
        assertThat(elena.getType()).isEqualTo(NotificationType.RESERVATION_UNASSIGNED_STAFF);
        assertThat(elena.getSubject()).startsWith("Removed from your schedule:");
        assertThat(elena.getCalendarIcs().replace("\r\n ", "")).contains("METHOD:CANCEL",
                "UID:3f1c9a52-8a8e-4a43-9a1e-1b2f0c7d9e11-staff-11@booking-platform");
    }

    @Test
    void an_employee_still_on_a_moved_reservation_hears_it_moved() {
        service.reservationRescheduled(payload(List.of(ELENA)), List.of(ELENA));

        Notification email = staffMessages().get("elena@spa.test");
        assertThat(email.getType()).isEqualTo(NotificationType.RESERVATION_RESCHEDULED_STAFF);
        assertThat(email.getSubject()).startsWith("Appointment moved:");
        assertThat(email.getCalendarIcs()).contains("METHOD:REQUEST");
    }

    @Test
    void a_reassignment_at_the_same_time_only_concerns_the_two_employees() {
        service.staffChanged(payload(List.of(ANNA)), List.of(ELENA));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getType).containsExactlyInAnyOrder(
                NotificationType.RESERVATION_CONFIRMED_STAFF, NotificationType.RESERVATION_UNASSIGNED_STAFF);
    }
}
