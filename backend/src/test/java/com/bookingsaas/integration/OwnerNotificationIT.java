package com.bookingsaas.integration;

import com.bookingsaas.business.BusinessService;
import com.bookingsaas.notification.NotificationComposer;
import com.bookingsaas.notification.NotificationType;
import com.bookingsaas.notification.ReservationNotificationPayload;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** The business hears about every new reservation, wherever it came from. */
@DisplayName("owner notifications")
class OwnerNotificationIT extends AbstractSpaIntegrationTest {

    @Autowired
    BusinessService businessService;

    @Autowired
    NotificationComposer composer;

    @Test
    void reservations_are_announced_to_the_owners_login_until_another_address_is_set() throws Exception {
        assertThat(businessService.notificationEmailOf(businessId)).isEqualTo("owner@spa.test");

        send(put("/api/admin/business/settings"), owner, settings("\"Alerts@Spa.Test\""), 200);
        assertThat(businessService.notificationEmailOf(businessId)).isEqualTo("alerts@spa.test");
        JsonNode saved = send(get("/api/admin/business/settings"), owner, null, 200);
        assertThat(saved.get("notificationEmail").asText()).isEqualTo("alerts@spa.test");

        // Cleared again: back to the owner.
        send(put("/api/admin/business/settings"), owner, settings("\"\""), 200);
        assertThat(businessService.notificationEmailOf(businessId)).isEqualTo("owner@spa.test");

        send(put("/api/admin/business/settings"), owner, settings("\"not an email\""), 400);
    }

    @Test
    void the_owners_email_says_where_the_booking_came_from_and_how_to_reach_the_customer() {
        ReservationNotificationPayload payload = new ReservationNotificationPayload(businessId, 7L, "Serenity Test Spa",
                "hello@spa.test", "+389 2 312 4567", "Main street 1", "Europe/Skopje", "Ana Petrova", null,
                "+38970555666", "INSTAGRAM", "Classic Massage", 1, "Elena",
                Instant.parse("2026-10-19T12:00:00Z"), Instant.parse("2026-10-19T13:00:00Z"), "ref", null, null);

        String subject = composer.subject(NotificationType.RESERVATION_CONFIRMED_BUSINESS, payload);
        String body = composer.body(NotificationType.RESERVATION_CONFIRMED_BUSINESS, payload);

        assertThat(subject).isEqualTo("New reservation: Classic Massage — Ana Petrova (via Instagram)");
        assertThat(body).contains("/admin/reservations")
                .containsPattern("Booked via:\\s+Instagram")
                .containsPattern("Phone:\\s+\\+38970555666")
                .containsPattern("With:\\s+Elena");
        // The customer's own email keeps the business's address and phone instead.
        String customerBody = composer.body(NotificationType.RESERVATION_CONFIRMED_CUSTOMER, payload);
        assertThat(customerBody).containsPattern("Phone:\\s+\\+389 2 312 4567").doesNotContain("Booked via");
    }

    private static String settings(String notificationEmail) {
        return """
                {"minimumBookingNoticeMinutes":60,"maximumBookingAdvanceDays":60,"slotIntervalMinutes":30,
                 "allowCustomerCancellation":true,"cancellationDeadlineHours":12,
                 "emailNotificationsEnabled":true,"reminderNotificationsEnabled":true,"reminderLeadHours":24,
                 "notificationEmail":%s}""".formatted(notificationEmail);
    }
}
