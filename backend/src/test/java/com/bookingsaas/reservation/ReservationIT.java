package com.bookingsaas.reservation;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Reservations end to end through the public API, against real PostgreSQL:
 * resource assignment, capacity, pricing, discounts, conflicts — including two
 * requests racing for the same resource — and cancellation.
 *
 * <p>The business: two treatment rooms (1 and 2 guests), one employee who can
 * treat up to two guests at once, and a 60-minute massage for up to two
 * people needing one room and one employee. Every requirement must cover the
 * whole group, so the employee's capacity matters as much as the room's.
 */
@DisplayName("reservations")
class ReservationIT extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Skopje");

    private String token;
    private String slug;
    private Long serviceId;
    private LocalDate day;

    @BeforeEach
    void setUpSpa() throws Exception {
        token = registerAndLogin("owner@spa.test", "Test Spa");
        slug = slugOf(token);
        openEveryDay(token);
        day = LocalDate.now(ZONE).plusDays(1);

        long roomType = send(post("/api/admin/resource-types"), token, """
                {"name":"Treatment Room","active":true}""", 201).get("id").asLong();
        send(post("/api/admin/resources"), token, """
                {"resourceTypeId":%d,"name":"Room Small","capacity":1,"active":true}""".formatted(roomType), 201);
        send(post("/api/admin/resources"), token, """
                {"resourceTypeId":%d,"name":"Room Large","capacity":2,"active":true}""".formatted(roomType), 201);
        send(post("/api/admin/employees"), token, """
                {"name":"Anna","active":true,"capacity":2}""", 201);

        serviceId = send(post("/api/admin/services"), token, """
                {"name":"Massage","durationMinutes":60,"price":50,"pricePerParticipant":false,"maxParticipants":2,
                 "bufferBeforeMinutes":0,"bufferAfterMinutes":0,"allowCustomerResourceSelection":false,"active":true,
                 "requirements":[
                   {"resourceTypeId":%d,"quantity":1,"required":true,"customerCanChoose":false},
                   {"resourceTypeId":%d,"quantity":1,"required":true,"customerCanChoose":false}]}
                """.formatted(roomType, employeeTypeId(token)), 201).get("id").asLong();

        send(post("/api/admin/discounts"), token, """
                {"name":"Ten percent","code":"TEN","type":"PERCENTAGE","value":10,"active":true,"maxUsage":1,
                 "appliesToAllServices":true}""", 201);
    }

    @Test
    void a_reservation_is_stored_with_its_assigned_resources_and_price() throws Exception {
        JsonNode reservation = reserve("10:00", 1, null, "ana@example.com", 201);

        assertThat(reservation.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(reservation.get("originalPrice").decimalValue()).isEqualByComparingTo("50");
        assertThat(reservation.get("discountAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(reservation.get("finalPrice").decimalValue()).isEqualByComparingTo("50");
        // Smallest adequate room for one guest.
        assertThat(resourceNames(reservation)).containsExactlyInAnyOrder("Room Small", "Anna");

        JsonNode admin = send(get("/api/admin/reservations?from=" + day + "&to=" + day), token, null, 200);
        assertThat(admin).hasSize(1);
        assertThat(admin.get(0).get("numberOfParticipants").asInt()).isEqualTo(1);
    }

    @Test
    void a_group_is_given_a_resource_with_enough_capacity() throws Exception {
        JsonNode reservation = reserve("10:00", 2, null, "pair@example.com", 201);

        assertThat(resourceNames(reservation)).contains("Room Large");
    }

    @Test
    void a_taken_resource_makes_overlapping_times_unavailable() throws Exception {
        reserve("10:00", 1, null, "first@example.com", 201);

        // Anna is the only employee: 10:30 overlaps her 10:00-11:00 reservation.
        JsonNode conflict = reserve("10:30", 1, null, "second@example.com", 409);
        assertThat(conflict.get("error").asText()).isEqualTo("RESOURCE_NOT_AVAILABLE");

        List<String> times = new ArrayList<>();
        send(get("/api/public/businesses/" + slug + "/services/" + serviceId + "/availability?from=" + day),
                null, null, 200).get(0).get("slots").forEach(slot -> times.add(slot.get("time").asText()));
        assertThat(times).doesNotContain("10:00:00", "10:30:00").contains("09:00:00", "11:00:00");
    }

    @Test
    void two_simultaneous_requests_cannot_reserve_the_same_resource() throws Exception {
        int attempts = 6;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            String email = "racer" + i + "@example.com";
            String racerToken = customerToken("racer" + i + "@example.com");
            Callable<Integer> attempt = () -> {
                start.await();
                return mockMvc.perform(post("/api/public/businesses/" + slug + "/reservations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(racerToken))
                                .contentType("application/json")
                                .content(body("14:00", 1, null, email)))
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
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(attempts - 1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from reservation where status <> 'CANCELLED'", Long.class)).isEqualTo(1L);
    }

    @Test
    void a_discount_code_is_applied_by_the_server_and_its_usage_limit_holds() throws Exception {
        JsonNode discounted = reserve("10:00", 1, "ten", "ana@example.com", 201);

        assertThat(discounted.get("discountCode").asText()).isEqualTo("TEN");
        assertThat(discounted.get("discountAmount").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(discounted.get("finalPrice").decimalValue()).isEqualByComparingTo("45.00");

        JsonNode exhausted = reserve("15:00", 1, "TEN", "bob@example.com", 422);
        assertThat(exhausted.get("error").asText()).isEqualTo("DISCOUNT_USAGE_LIMIT_REACHED");

        // Cancelling gives the use back.
        cancel(adminIdOf(discounted));
        reserve("15:00", 1, "TEN", "bob@example.com", 201);
    }

    @Test
    void an_unknown_discount_code_is_rejected_and_nothing_is_written() throws Exception {
        JsonNode rejected = reserve("10:00", 1, "NOPE", "ana@example.com", 422);

        assertThat(rejected.get("error").asText()).isEqualTo("DISCOUNT_INVALID");
        assertThat(jdbcTemplate.queryForObject("select count(*) from reservation", Long.class)).isZero();
    }

    @Test
    void cancelling_releases_the_resources() throws Exception {
        JsonNode first = reserve("10:00", 1, null, "ana@example.com", 201);
        cancel(adminIdOf(first));

        reserve("10:00", 1, null, "bob@example.com", 201);
    }

    @Test
    void moving_a_reservation_keeps_its_resources_and_respects_conflicts() throws Exception {
        long first = adminIdOf(reserve("10:00", 1, null, "ana@example.com", 201));
        reserve("15:00", 1, null, "bob@example.com", 201);

        JsonNode moved = send(put("/api/admin/reservations/" + first + "/schedule"), token, """
                {"startTime":"%sT12:00:00"}""".formatted(day), 200);
        assertThat(moved.get("localStartTime").asText()).endsWith("12:00:00");
        assertThat(resourceNames(moved)).containsExactlyInAnyOrder("Room Small", "Anna");

        // Anna is taken at 15:00 by the other reservation.
        JsonNode conflict = send(put("/api/admin/reservations/" + first + "/schedule"), token, """
                {"startTime":"%sT15:30:00"}""".formatted(day), 409);
        assertThat(conflict.get("error").asText()).isEqualTo("RESOURCE_NOT_AVAILABLE");
    }

    @Test
    void a_returning_customer_is_recognised_by_email() throws Exception {
        reserve("10:00", 1, null, "Ana@Example.com", 201);
        reserve("12:00", 1, null, "ana@example.com", 201);

        JsonNode customers = send(get("/api/admin/customers"), token, null, 200).get("content");
        assertThat(customers).hasSize(1);
        assertThat(customers.get(0).get("reservationCount").asLong()).isEqualTo(2);
        assertThat(customers.get(0).get("totalSpent").decimalValue()).isEqualByComparingTo("100");
    }

    @Test
    void booking_requires_a_signed_in_account() throws Exception {
        JsonNode anonymous = send(post("/api/public/businesses/" + slug + "/reservations"), null,
                body("10:00", 1, null, "anon@example.com"), 401);
        assertThat(anonymous.get("error").asText()).isEqualTo("UNAUTHENTICATED");

        // Browsing stays open.
        send(get("/api/public/businesses/" + slug + "/services/" + serviceId + "/availability?from=" + day),
                null, null, 200);
    }

    @Test
    void a_customer_account_cannot_reach_the_admin_api() throws Exception {
        JsonNode denied = send(get("/api/admin/customers"), customerToken("shopper@example.com"), null, 403);
        assertThat(denied.get("error").asText()).isEqualTo("NO_BUSINESS_CONTEXT");
    }

    @Test
    void the_reservation_takes_the_accounts_email_and_needs_a_phone() throws Exception {
        String token = customerToken("owner.of.account@example.com");

        JsonNode missingPhone = send(post("/api/public/businesses/" + slug + "/reservations"), token, """
                {"serviceId":%d,"participants":1,"startTime":"%sT10:00:00",
                 "customer":{"firstName":"No","lastName":"Phone"}}
                """.formatted(serviceId, day), 400);
        assertThat(missingPhone.get("fieldErrors").has("customer.phone")).isTrue();

        JsonNode badPhone = send(post("/api/public/businesses/" + slug + "/reservations"), token, """
                {"serviceId":%d,"participants":1,"startTime":"%sT10:00:00",
                 "customer":{"firstName":"Bad","lastName":"Phone","phone":"+389 12"}}
                """.formatted(serviceId, day), 400);
        assertThat(badPhone.get("fieldErrors").has("customer.phone")).isTrue();

        // An email in the request is ignored: nobody can book in someone else's name.
        send(post("/api/public/businesses/" + slug + "/reservations"), token, """
                {"serviceId":%d,"participants":1,"startTime":"%sT10:00:00",
                 "customer":{"firstName":"Some","lastName":"One","email":"victim@example.com","phone":"+389 70 111 222"}}
                """.formatted(serviceId, day), 201);
        JsonNode customers = send(get("/api/admin/customers"), this.token, null, 200).get("content");
        assertThat(customers).hasSize(1);
        assertThat(customers.get(0).get("email").asText()).isEqualTo("owner.of.account@example.com");
    }

    @Test
    void times_outside_opening_hours_and_oversized_groups_are_rejected() throws Exception {
        assertThat(reserve("19:30", 1, null, "late@example.com", 422).get("error").asText())
                .isEqualTo("OUTSIDE_BUSINESS_HOURS");
        assertThat(reserve("10:00", 3, null, "group@example.com", 400).get("error").asText())
                .isEqualTo("INVALID_PARTICIPANTS");
    }

    // --- helpers -----------------------------------------------------------

    private JsonNode reserve(String time, int participants, String code, String email, int expectedStatus)
            throws Exception {
        return send(post("/api/public/businesses/" + slug + "/reservations"), customerToken(email),
                body(time, participants, code, email), expectedStatus);
    }

    private String body(String time, int participants, String code, String email) {
        return """
                {"serviceId":%d,"participants":%d,"startTime":"%sT%s:00",%s
                 "customer":{"firstName":"Test","lastName":"Customer","email":"%s","phone":"+389 70 %s"}}
                """.formatted(serviceId, participants, day, time,
                code == null ? "" : "\"discountCode\":\"" + code + "\",", email, phoneFor(email));
    }

    /** A stable phone number per email, so one person keeps one number across bookings. */
    private static String phoneFor(String email) {
        return String.format("%06d", Math.abs(email.toLowerCase().hashCode() % 1_000_000));
    }

    private long adminIdOf(JsonNode publicReservation) throws Exception {
        String reference = publicReservation.get("reference").asText();
        for (JsonNode reservation : send(get("/api/admin/reservations?from=" + day + "&to=" + day), token, null, 200)) {
            if (reservation.get("reference").asText().equals(reference)) {
                return reservation.get("id").asLong();
            }
        }
        throw new AssertionError("reservation " + reference + " not listed");
    }

    private void cancel(long id) throws Exception {
        send(patch("/api/admin/reservations/" + id), token, """
                {"status":"CANCELLED","reason":"test"}""", 200);
    }

    private static List<String> resourceNames(JsonNode reservation) {
        List<String> names = new ArrayList<>();
        reservation.get("resources").forEach(resource -> names.add(resource.get("name").asText()));
        return names;
    }
}
