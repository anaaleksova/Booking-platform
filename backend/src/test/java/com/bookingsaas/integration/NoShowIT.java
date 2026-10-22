package com.bookingsaas.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** No-shows, as the business records them: what marking one does, and where it is counted. */
@DisplayName("no-shows")
class NoShowIT extends AbstractSpaIntegrationTest {

    private static final String EMAIL = "tomas@example.com";
    private static final String PHONE = "+38970555666";

    @Test
    void a_no_show_can_only_be_marked_once_it_started_and_releases_the_resources() throws Exception {
        long id = adminReservation("10:00", null);
        send(patch("/api/admin/reservations/" + id), owner, "{\"status\":\"NO_SHOW\"}", 422);

        moveToThePast(id);
        JsonNode marked = send(patch("/api/admin/reservations/" + id), owner, "{\"status\":\"NO_SHOW\"}", 200);
        assertThat(marked.get("status").asText()).isEqualTo("NO_SHOW");
        assertThat(count("select count(*) from reservation_resource where reservation_id = " + id + " and occupying"))
                .isZero();

        // The wrong button: a no-show can be turned back into a completed visit, and the reverse.
        send(patch("/api/admin/reservations/" + id), owner, "{\"status\":\"COMPLETED\"}", 200);
        assertThat(count("select count(*) from reservation_resource where reservation_id = " + id + " and occupying"))
                .isEqualTo(2);
        send(patch("/api/admin/reservations/" + id), owner, "{\"status\":\"NO_SHOW\"}", 200);
        send(patch("/api/admin/reservations/" + id), owner, "{\"status\":\"CANCELLED\"}", 422);
    }

    @Test
    void no_shows_are_recorded_but_never_stop_the_customer_from_booking() throws Exception {
        long customerId = noShows(3);

        send(post("/api/public/businesses/" + slug() + "/reservations"), customerToken(EMAIL), """
                {"serviceId":%d,"participants":1,"startTime":"%sT15:00:00",
                 "customer":{"firstName":"Tomas","lastName":"Berg","phone":"%s"}}
                """.formatted(classicId, day, PHONE), 201);

        JsonNode history = send(get("/api/admin/customers/" + customerId + "/reservations"), owner, null, 200);
        assertThat(history.get("noShowReservations").asInt()).isEqualTo(3);
        // No-shows are not visits.
        assertThat(history.get("totalReservations").asInt()).isEqualTo(1);
        assertThat(history.path("lastVisitAt").isNull() || history.path("lastVisitAt").isMissingNode()).isTrue();
    }

    @Test
    void analytics_count_no_shows_apart_from_visits() throws Exception {
        noShows(1);
        long kept = adminReservation("15:00", null);
        moveToThePast(kept);
        send(patch("/api/admin/reservations/" + kept), owner, "{\"status\":\"COMPLETED\"}", 200);

        String from = day.minusDays(5).toString();
        JsonNode report = send(get("/api/admin/analytics/reservations?from=" + from + "&to=" + day), owner, null,
                200);
        assertThat(report.get("noShows").asInt()).isEqualTo(1);
        assertThat(report.get("noShowRate").asDouble()).isEqualTo(0.5);
        assertThat(report.get("total").asInt()).isEqualTo(1);
    }

    // --- helpers -------------------------------------------------------------

    /** Gives Tomas this many past no-shows (one hour apart) and returns his customer id. */
    private long noShows(int count) throws Exception {
        long customerId = 0;
        for (int i = 0; i < count; i++) {
            Long existing = jdbcTemplate.query("select id from customer where email = ?",
                    rs -> rs.next() ? rs.getLong(1) : null, EMAIL);
            long id = adminReservation("%02d:00".formatted(8 + (int) count("select count(*) from reservation")),
                    existing);
            moveToThePast(id);
            send(patch("/api/admin/reservations/" + id), owner, "{\"status\":\"NO_SHOW\"}", 200);
            customerId = send(get("/api/admin/reservations/" + id), owner, null, 200).get("customerId").asLong();
        }
        return customerId;
    }

    private long adminReservation(String time, Long customerId) throws Exception {
        String who = customerId != null ? "\"customerId\":" + customerId
                : "\"customer\":{\"firstName\":\"Tomas\",\"lastName\":\"Berg\",\"email\":\"%s\",\"phone\":\"%s\"}"
                        .formatted(EMAIL, PHONE);
        return send(post("/api/admin/reservations"), owner, """
                {"serviceId":%d,"participants":1,"startTime":"%sT%s:00",%s}
                """.formatted(classicId, day, time, who), 201).get("id").asLong();
    }

    /** Three days earlier: from tomorrow to the day before yesterday. */
    private void moveToThePast(long reservationId) {
        jdbcTemplate.update("""
                update reservation set start_time = start_time - interval '3 days', end_time = end_time - interval '3 days',
                       block_start_time = block_start_time - interval '3 days',
                       block_end_time = block_end_time - interval '3 days'
                where id = ?""", reservationId);
        jdbcTemplate.update("""
                update reservation_resource set block_start_time = block_start_time - interval '3 days',
                       block_end_time = block_end_time - interval '3 days'
                where reservation_id = ?""", reservationId);
    }
}
