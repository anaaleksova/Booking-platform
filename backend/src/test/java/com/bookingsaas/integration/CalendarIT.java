package com.bookingsaas.integration;

import com.bookingsaas.notification.NotificationComposer;
import com.bookingsaas.notification.NotificationType;
import com.bookingsaas.notification.ReservationNotificationPayload;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reservations in calendars: invitations that keep the owner's calendar in
 * step, and the customer's own "Add to calendar".
 */
@DisplayName("calendar")
class CalendarIT extends AbstractSpaIntegrationTest {

    private static final String REFERENCE = "3f1c9a52-8a8e-4a43-9a1e-1b2f0c7d9e11";
    private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");

    @Autowired
    NotificationComposer composer;

    @Test
    void the_owner_gets_an_invitation_for_a_new_booking_and_updates_when_it_moves_or_is_cancelled() {
        ReservationNotificationPayload payload = payload();

        String created = unfold(composer.calendarFile(NotificationType.RESERVATION_CONFIRMED_BUSINESS, payload,
                "owner@spa.test", NOW));
        assertThat(created).contains("METHOD:REQUEST", "SUMMARY:Classic Massage · Ana Petrova",
                        "UID:" + REFERENCE + "-business@booking-platform",
                        "PARTSTAT=ACCEPTED;RSVP=FALSE:mailto:owner@spa.test", "Phone: +38970555666",
                        "Booked via: Instagram")
                .contains("DTSTART:20261019T120000Z");

        String moved = unfold(composer.calendarFile(NotificationType.RESERVATION_RESCHEDULED_BUSINESS, payload,
                "owner@spa.test", NOW.plusSeconds(60)));
        assertThat(moved).contains("METHOD:REQUEST", "UID:" + REFERENCE + "-business@booking-platform");
        assertThat(sequence(moved)).isGreaterThan(sequence(created));

        String cancelled = unfold(composer.calendarFile(NotificationType.RESERVATION_CANCELLED_BUSINESS, payload,
                "owner@spa.test", NOW.plusSeconds(120)));
        assertThat(cancelled).contains("METHOD:CANCEL", "STATUS:CANCELLED",
                "UID:" + REFERENCE + "-business@booking-platform");
        assertThat(sequence(cancelled)).isGreaterThan(sequence(moved));

        assertThat(composer.subject(NotificationType.RESERVATION_RESCHEDULED_BUSINESS, payload))
                .isEqualTo("Reservation moved: Classic Massage — Ana Petrova");
    }

    @Test
    void a_customer_can_download_their_reservation_as_a_calendar_file() throws Exception {
        JsonNode booked = send(post("/api/public/businesses/" + slug() + "/reservations"),
                customerToken("web@example.com"), """
                        {"serviceId":%d,"participants":1,"startTime":"%sT14:00:00",
                         "customer":{"firstName":"Web","lastName":"Guest","phone":"+38970111333"}}
                        """.formatted(classicId, day), 201);
        String reference = booked.get("reference").asText();

        MvcResult result = mockMvc.perform(get("/api/public/reservations/" + reference + "/calendar.ics"))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentType()).startsWith("text/calendar");
        assertThat(unfold(result.getResponse().getContentAsString(StandardCharsets.UTF_8)))
                .contains("METHOD:PUBLISH", "SUMMARY:Classic Massage at Serenity Test Spa",
                        "UID:" + reference + "@booking-platform", "/booking/" + reference)
                .doesNotContain("ATTENDEE");
    }

    @Test
    void the_customers_confirmation_offers_add_to_calendar_and_attaches_a_plain_file() {
        ReservationNotificationPayload payload = payload();

        String html = composer.html(NotificationType.RESERVATION_CONFIRMED_CUSTOMER, payload,
                new NotificationComposer.Branding(null, false));
        assertThat(html).contains("Add it to your calendar", "https://calendar.google.com/calendar/render",
                "outlook.live.com", "/api/public/reservations/" + REFERENCE + "/calendar.ics");
        assertThat(composer.calendarFile(NotificationType.RESERVATION_CONFIRMED_CUSTOMER, payload,
                "ana@example.com", NOW)).contains("METHOD:PUBLISH").doesNotContain("ATTENDEE");

        // Nothing calendar-like for a customer's cancellation.
        assertThat(composer.calendarFile(NotificationType.RESERVATION_CANCELLED_CUSTOMER, payload,
                "ana@example.com", NOW)).isNull();
        assertThat(composer.html(NotificationType.RESERVATION_CANCELLED_CUSTOMER, payload,
                new NotificationComposer.Branding(null, false))).doesNotContain("Add it to your calendar");
    }

    @Test
    void the_old_subscription_link_is_gone() throws Exception {
        send(get("/api/admin/calendar-feed"), owner, null, 404);
    }

    private ReservationNotificationPayload payload() {
        return new ReservationNotificationPayload(businessId, 7L, "Serenity Test Spa", "hello@spa.test",
                "+389 2 312 4567", "Main street 1", "Europe/Skopje", "Ana Petrova", "ana@example.com",
                "+38970555666", "INSTAGRAM", "Classic Massage", 1, "Anna",
                Instant.parse("2026-10-19T12:00:00Z"), Instant.parse("2026-10-19T13:00:00Z"), REFERENCE, null, null);
    }

    private static long sequence(String ics) {
        return Long.parseLong(ics.lines().filter(line -> line.startsWith("SEQUENCE:")).findFirst().orElseThrow()
                .substring("SEQUENCE:".length()));
    }

    /** As a calendar app reads it: long lines are split with CRLF + space. */
    private static String unfold(String ics) {
        return ics.replace("\r\n ", "");
    }
}
