package com.bookingsaas.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The integration API end to end: token security, the same availability and
 * rules as the website, idempotent reservation creation, social identities
 * and cancellation.
 *
 * <p>The business is the spa of {@link AbstractSpaIntegrationTest}.
 */
@DisplayName("integration API")
class IntegrationApiIT extends AbstractSpaIntegrationTest {

    // --- security ----------------------------------------------------------

    @Test
    void the_api_requires_an_integration_token_and_accepts_nothing_else() throws Exception {
        assertThat(send(get("/api/integrations/me"), null, null, 401).get("error").asText())
                .isEqualTo("UNAUTHENTICATED");
        integration(get("/api/integrations/me"), "bkp_not-a-real-token", null, 401);
        // A signed-in owner's session is not an integration token...
        send(get("/api/integrations/businesses/" + businessId + "/services"), owner, null, 401);
        // ...and an integration token opens no admin endpoint.
        send(get("/api/admin/reservations"), token, null, 401);

        JsonNode me = integration(get("/api/integrations/me"), token, null, 200);
        assertThat(me.get("businessId").asLong()).isEqualTo(businessId);
    }

    @Test
    void a_token_only_reaches_its_own_business_and_its_own_permissions() throws Exception {
        String otherOwner = registerAndLogin("owner@other.test", "Other Business");
        long otherBusiness = send(get("/api/admin/business"), otherOwner, null, 200).get("id").asLong();

        integration(get("/api/integrations/businesses/" + otherBusiness + "/services"), token, null, 403);

        String readOnly = send(post("/api/admin/integrations/tokens"), owner, """
                {"name":"read only","permissions":["READ_SERVICES"]}""", 201).get("secret").asText();
        integration(get("/api/integrations/businesses/" + businessId + "/services"), readOnly, null, 200);
        integration(post("/api/integrations/businesses/" + businessId + "/reservations")
                .header("Idempotency-Key", "key-00000001"), readOnly, reservationBody(classicId, 1, "10:00", "1001"), 403);
    }

    @Test
    void a_revoked_token_stops_working() throws Exception {
        long tokenId = send(get("/api/admin/integrations"), owner, null, 200).get("tokens").get(0).get("id").asLong();
        send(delete("/api/admin/integrations/tokens/" + tokenId), owner, null, 200);

        integration(get("/api/integrations/me"), token, null, 401);
    }

    @Test
    void only_the_owner_manages_integrations() throws Exception {
        String customer = customerToken("someone@example.com");
        send(get("/api/admin/integrations"), customer, null, 403);
    }

    // --- catalogue and availability -----------------------------------------------

    @Test
    void services_and_availability_come_from_the_same_engine_as_the_website() throws Exception {
        JsonNode services = integration(get("/api/integrations/businesses/" + businessId + "/services"), token, null, 200);
        assertThat(services).extracting(node -> node.get("name").asText())
                .containsExactlyInAnyOrder("Classic Massage", "Couples Massage");

        JsonNode availability = integration(get("/api/integrations/businesses/" + businessId
                + "/availability?serviceId=" + couplesId + "&date=" + day + "&numberOfParticipants=2"), token, null, 200);
        assertThat(availability.get("durationMinutes").asInt()).isEqualTo(60);
        List<String> starts = new ArrayList<>();
        availability.get("availableSlots").forEach(slot -> starts.add(slot.get("start").asText()));
        assertThat(starts).contains("10:00", "17:30");

        JsonNode website = send(get("/api/public/businesses/" + slug() + "/services/" + couplesId
                + "/availability?from=" + day + "&participants=2"), null, null, 200);
        assertThat(website.get(0).get("slots").size()).isEqualTo(availability.get("availableSlots").size());

        // A group the service does not allow is refused, not silently shrunk.
        integration(get("/api/integrations/businesses/" + businessId + "/availability?serviceId=" + classicId
                + "&date=" + day + "&numberOfParticipants=2"), token, null, 400);
    }

    // --- reservations ----------------------------------------------------------

    @Test
    void a_reservation_is_created_with_the_server_price_and_shows_up_in_the_admin_panel() throws Exception {
        JsonNode created = integration(post("/api/integrations/businesses/" + businessId + "/reservations")
                        .header("Idempotency-Key", "booking-attempt-1"), token,
                reservationBody(couplesId, 2, "17:30", "1001"), 201);

        assertThat(created.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(created.get("source").asText()).isEqualTo("INSTAGRAM");
        assertThat(created.get("finalPrice").decimalValue()).isEqualByComparingTo("80");
        assertThat(created.get("start").asText()).isEqualTo("17:30");
        assertThat(created.get("resources")).hasSize(2);
        assertThat(created.get("channels").get(0).get("externalUserId").asText()).isEqualTo("ig-1001");

        JsonNode admin = send(get("/api/admin/reservations?from=" + day + "&to=" + day), owner, null, 200);
        assertThat(admin).hasSize(1);
        assertThat(admin.get(0).get("source").asText()).isEqualTo("INSTAGRAM");
        assertThat(admin.get(0).get("customerName").asText()).isEqualTo("Ana Petrova");
    }

    @Test
    void repeating_a_request_with_the_same_idempotency_key_returns_the_first_reservation() throws Exception {
        String path = "/api/integrations/businesses/" + businessId + "/reservations";
        String body = reservationBody(couplesId, 2, "10:00", "1001");
        JsonNode first = integration(post(path).header("Idempotency-Key", "retry-key-1"), token, body, 201);
        JsonNode again = integration(post(path).header("Idempotency-Key", "retry-key-1"), token, body, 200);

        assertThat(again.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(count("select count(*) from reservation")).isEqualTo(1);

        // The same key for a different booking is a client bug, not a replay.
        JsonNode reused = integration(post(path).header("Idempotency-Key", "retry-key-1"), token,
                reservationBody(couplesId, 2, "14:00", "1001"), 422);
        assertThat(reused.get("error").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        integration(post(path), token, body, 400);   // the key is required
    }

    @Test
    void simultaneous_retries_with_one_key_create_one_reservation() throws Exception {
        int attempts = 5;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        String body = reservationBody(couplesId, 2, "12:00", "2002");
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Callable<Integer> attempt = () -> {
                start.await();
                return mockMvc.perform(post("/api/integrations/businesses/" + businessId + "/reservations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .header("Idempotency-Key", "same-key-for-all")
                                .contentType("application/json").content(body))
                        .andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(attempt));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 200).hasSize(attempts - 1);
        assertThat(count("select count(*) from reservation")).isEqualTo(1);
    }

    @Test
    void a_taken_slot_is_refused_with_the_same_conflict_as_on_the_website() throws Exception {
        String path = "/api/integrations/businesses/" + businessId + "/reservations";
        integration(post(path).header("Idempotency-Key", "first-guest-1"), token,
                reservationBody(couplesId, 2, "11:00", "1001"), 201);
        // Anna treats up to two guests and is busy: the next couple cannot fit.
        JsonNode conflict = integration(post(path).header("Idempotency-Key", "second-guest-1"), token,
                reservationBody(couplesId, 2, "11:30", "3003"), 409);
        assertThat(conflict.get("error").asText()).isEqualTo("RESOURCE_NOT_AVAILABLE");
    }

    // --- customers -------------------------------------------------------------

    @Test
    void a_social_user_is_linked_to_the_customer_created_for_them_and_recognised_later() throws Exception {
        String path = "/api/integrations/businesses/" + businessId + "/customers";
        JsonNode created = integration(post(path), token, """
                {"provider":"INSTAGRAM","externalUserId":"ig-77","firstName":"Mila","phone":"+389 70 123 456"}
                """, 200);
        assertThat(created.get("created").asBoolean()).isTrue();
        assertThat(created.get("linked").asBoolean()).isTrue();
        assertThat(created.get("phone").asText()).isEqualTo("+38970123456");

        JsonNode found = integration(get(path + "?provider=INSTAGRAM&externalUserId=ig-77"), token, null, 200);
        assertThat(found.get("id").asLong()).isEqualTo(created.get("id").asLong());
        integration(get(path + "?provider=FACEBOOK&externalUserId=ig-77"), token, null, 404);
    }

    @Test
    void contact_details_of_an_existing_customer_do_not_link_a_stranger_to_them() throws Exception {
        send(post("/api/admin/customers"), owner, """
                {"firstName":"Regular","lastName":"Guest","phone":"+38970999888"}""", 201);

        JsonNode resolved = integration(post("/api/integrations/businesses/" + businessId + "/customers"), token, """
                {"provider":"INSTAGRAM","externalUserId":"ig-stranger","firstName":"X","phone":"+38970999888"}
                """, 200);
        assertThat(resolved.get("linked").asBoolean()).isFalse();
        integration(get("/api/integrations/businesses/" + businessId
                + "/customers?provider=INSTAGRAM&externalUserId=ig-stranger"), token, null, 404);
    }

    // --- cancellation --------------------------------------------------------

    @Test
    void a_cancellation_frees_the_slot_and_a_repeat_is_harmless() throws Exception {
        JsonNode created = integration(post("/api/integrations/businesses/" + businessId + "/reservations")
                        .header("Idempotency-Key", "to-cancel-001"), token,
                reservationBody(couplesId, 2, "15:00", "1001"), 201);
        String cancel = "/api/integrations/businesses/" + businessId + "/reservations/" + created.get("id").asLong()
                + "/cancel";

        assertThat(integration(post(cancel), token, "{}", 200).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(integration(post(cancel), token, "{}", 200).get("status").asText()).isEqualTo("CANCELLED");

        integration(post("/api/integrations/businesses/" + businessId + "/reservations")
                        .header("Idempotency-Key", "rebook-15-00"), token,
                reservationBody(couplesId, 2, "15:00", "3003"), 201);
    }

    @Test
    void the_customer_cancellation_policy_applies() throws Exception {
        JsonNode created = integration(post("/api/integrations/businesses/" + businessId + "/reservations")
                        .header("Idempotency-Key", "policy-test-1"), token,
                reservationBody(couplesId, 2, "09:00", "1001"), 201);
        send(put("/api/admin/business/settings"), owner, """
                {"minimumBookingNoticeMinutes":0,"maximumBookingAdvanceDays":60,"slotIntervalMinutes":30,
                 "allowCustomerCancellation":false,"cancellationDeadlineHours":0,
                 "emailNotificationsEnabled":false,"reminderNotificationsEnabled":false,"reminderLeadHours":24}
                """, 200);

        JsonNode refused = integration(post("/api/integrations/businesses/" + businessId + "/reservations/"
                + created.get("id").asLong() + "/cancel"), token, "{}", 403);
        assertThat(refused.get("error").asText()).isEqualTo("CANCELLATION_NOT_ALLOWED");
    }

    // --- events and analytics ----------------------------------------------------

    @Test
    void reservation_events_are_queued_for_n8n_only_when_it_is_connected() throws Exception {
        String path = "/api/integrations/businesses/" + businessId + "/reservations";
        integration(post(path).header("Idempotency-Key", "before-connect"), token,
                reservationBody(couplesId, 2, "09:00", "1001"), 201);
        assertThat(count("select count(*) from integration_event")).isZero();

        send(put("/api/admin/integrations/n8n"), owner, """
                {"webhookUrl":"http://127.0.0.1:9/webhook/booking-events","enabled":true}""", 200);
        send(put("/api/admin/business/settings"), owner, """
                {"minimumBookingNoticeMinutes":0,"maximumBookingAdvanceDays":60,"slotIntervalMinutes":30,
                 "allowCustomerCancellation":true,"cancellationDeadlineHours":0,
                 "emailNotificationsEnabled":false,"reminderNotificationsEnabled":true,"reminderLeadHours":2}
                """, 200);
        JsonNode created = integration(post(path).header("Idempotency-Key", "after-connect"), token,
                reservationBody(couplesId, 2, "18:00", "4004"), 201);
        assertThat(types()).containsExactlyInAnyOrder("reservation.created", "reservation.reminder_due");

        integration(post(path + "/" + created.get("id").asLong() + "/cancel"), token, "{}", 200);
        assertThat(types()).contains("reservation.cancelled");
        assertThat(count("""
                select count(*) from integration_event
                where event_type = 'reservation.reminder_due' and status = 'PENDING'""")).isZero();

        JsonNode overview = send(get("/api/admin/integrations"), owner, null, 200);
        assertThat(overview.get("n8n").get("status").asText()).isEqualTo("CONNECTED");
        assertThat(overview.get("n8n").get("signingSecret").asText()).startsWith("whsec_");
    }

    @Test
    void analytics_split_reservations_and_revenue_by_source() throws Exception {
        integration(post("/api/integrations/businesses/" + businessId + "/reservations")
                        .header("Idempotency-Key", "analytics-ig-1"), token,
                reservationBody(couplesId, 2, "10:00", "1001"), 201);
        send(post("/api/public/businesses/" + slug() + "/reservations"), customerToken("web@example.com"), """
                {"serviceId":%d,"participants":1,"startTime":"%sT14:00:00",
                 "customer":{"firstName":"Web","lastName":"Guest","phone":"+38970111222"}}
                """.formatted(classicId, day), 201);

        JsonNode sources = send(get("/api/admin/analytics/sources?from=" + day + "&to=" + day), owner, null, 200);
        JsonNode instagram = bySource(sources, "INSTAGRAM");
        JsonNode web = bySource(sources, "WEB");
        assertThat(instagram.get("reservations").asLong()).isEqualTo(1);
        assertThat(instagram.get("revenue").decimalValue()).isEqualByComparingTo("80");
        assertThat(web.get("reservations").asLong()).isEqualTo(1);
        assertThat(instagram.get("share").asDouble()).isEqualTo(0.5);
        assertThat(bySource(sources, "FACEBOOK").get("reservations").asLong()).isZero();
    }

    // --- helpers -------------------------------------------------------------

    private List<String> types() {
        return jdbcTemplate.queryForList("select event_type from integration_event", String.class);
    }

    private static JsonNode bySource(JsonNode sources, String source) {
        for (JsonNode node : sources) {
            if (node.get("source").asText().equals(source)) {
                return node;
            }
        }
        throw new AssertionError("no row for " + source);
    }
}
