package com.bookingsaas.notification;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.BusinessSettings;
import com.bookingsaas.common.config.AppProperties;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The owner's calendar follows reservations through invitations in the emails they already get. */
class OwnerCalendarInvitationTest {

    private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");
    private static final AppProperties PROPERTIES = new AppProperties(null, null, "https://book.example.com",
            new AppProperties.Notifications("bookings@example.com", true, 5000, 60000), null, null);

    private final NotificationComposer composer = new NotificationComposer(PROPERTIES);

    private static ReservationNotificationPayload payload() {
        return new ReservationNotificationPayload(1L, 7L, "Serenity Spa", "hello@spa.test", "+389 2 312 4567",
                "Main street 1", "Europe/Skopje", "Ana Petrova", "ana@example.com", "+38970555666", "WEB",
                "Classic Massage", 1, "Anna", Instant.parse("2026-10-19T12:00:00Z"),
                Instant.parse("2026-10-19T13:00:00Z"), "3f1c9a52-8a8e-4a43-9a1e-1b2f0c7d9e11", "Previously: earlier", null);
    }

    @Test
    void a_moved_reservation_is_announced_to_the_owner_with_an_invitation() {
        NotificationRepository repository = mock(NotificationRepository.class);
        BusinessService businessService = mock(BusinessService.class);
        BusinessSettings settings = new BusinessSettings(1L);
        settings.setReminderNotificationsEnabled(false);
        when(businessService.settingsOf(1L)).thenReturn(settings);
        when(businessService.notificationEmailOf(1L)).thenReturn("owner@spa.test");
        when(businessService.requireById(1L)).thenReturn(new Business());
        NotificationService service = new NotificationService(repository, composer, businessService, PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.reservationRescheduled(payload(), List.of());

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(2)).save(saved.capture());
        Notification owner = saved.getAllValues().stream()
                .filter(n -> n.getType() == NotificationType.RESERVATION_RESCHEDULED_BUSINESS).findFirst().orElseThrow();
        assertThat(owner.getRecipient()).isEqualTo("owner@spa.test");
        assertThat(owner.getCalendarIcs().replace("\r\n ", "")).contains("METHOD:REQUEST", "mailto:owner@spa.test",
                "ORGANIZER;CN=\"Serenity Spa\":mailto:bookings@example.com");
    }

    @Test
    void an_invitation_email_carries_the_calendar_as_an_alternative_and_as_an_attachment() throws Exception {
        Notification notification = new Notification();
        notification.setType(NotificationType.RESERVATION_CONFIRMED_BUSINESS);
        notification.setRecipient("owner@spa.test");
        notification.setSubject("New reservation");
        notification.setBody("A new reservation has been received.");
        notification.setHtmlBody("<p>A new reservation has been received.</p>");
        notification.setCalendarIcs(composer.calendarFile(NotificationType.RESERVATION_CONFIRMED_BUSINESS, payload(),
                "owner@spa.test", NOW));

        List<String> types = partTypes(send(notification));

        assertThat(types).anyMatch(type -> type.startsWith("multipart/mixed"))
                .anyMatch(type -> type.startsWith("multipart/alternative"))
                .anyMatch(type -> type.startsWith("text/plain"))
                .anyMatch(type -> type.startsWith("text/html"))
                .anyMatch(type -> type.startsWith("text/calendar") && type.contains("method=request"))
                .anyMatch(type -> type.startsWith("application/ics"));
    }

    @Test
    void a_customers_calendar_file_is_a_plain_attachment_not_an_invitation() throws Exception {
        Notification notification = new Notification();
        notification.setType(NotificationType.RESERVATION_CONFIRMED_CUSTOMER);
        notification.setRecipient("ana@example.com");
        notification.setSubject("Confirmed");
        notification.setBody("Confirmed.");
        notification.setHtmlBody("<p>Confirmed.</p>");
        notification.setCalendarIcs(composer.calendarFile(NotificationType.RESERVATION_CONFIRMED_CUSTOMER, payload(),
                "ana@example.com", NOW));

        List<String> types = partTypes(send(notification));

        assertThat(types).anyMatch(type -> type.startsWith("text/calendar") && type.contains("method=publish"))
                .noneMatch(type -> type.contains("method=request"));
    }

    /** Sends through the real sender into a captured message, then reads it back as a mail client would. */
    private static MimeMessage send(Notification notification) throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        Session session = Session.getInstance(new Properties());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(session));
        new EmailNotificationSender(mailSender, PROPERTIES).send(notification);
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());

        MimeMessage message = sent.getValue();
        message.saveChanges();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        return new MimeMessage(session, new ByteArrayInputStream(raw.toByteArray()));
    }

    private static List<String> partTypes(Part part) throws Exception {
        List<String> types = new ArrayList<>();
        types.add(part.getContentType().toLowerCase().replaceAll("\\s+", " "));
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                types.addAll(partTypes(multipart.getBodyPart(i)));
            }
        }
        return types;
    }
}
