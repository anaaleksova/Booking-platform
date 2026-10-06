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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Tables combined for larger groups, end to end: the availability offers
 * combinations nobody configured, one reservation holds every table of the one
 * chosen, and no table ends up in two overlapping reservations — not even when
 * two groups race for overlapping combinations.
 */
@DisplayName("combined resources")
class CombinedResourcesIT extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Skopje");

    private String token;
    private String slug;
    private long serviceId;
    private long requirementId;
    private long table1;
    private long table2;
    private long table3;
    private long table4;
    private LocalDate day;

    @BeforeEach
    void setUp() throws Exception {
        token = registerAndLogin("owner@bistro.test", "Lake Bistro");
        slug = slugOf(token);
        openEveryDay(token);
        day = LocalDate.now(ZONE).plusDays(1);

        long tableType = send(post("/api/admin/resource-types"), token, """
                {"name":"Table","active":true}""", 201).get("id").asLong();
        table1 = table(tableType, "Table 1", 2);
        table2 = table(tableType, "Table 2", 2);
        table3 = table(tableType, "Table 3", 4);
        table4 = table(tableType, "Table 4", 4);
        JsonNode service = send(post("/api/admin/services"), token, """
                {"name":"Dinner","durationMinutes":60,"price":0,"pricePerParticipant":false,"maxParticipants":12,
                 "bufferBeforeMinutes":0,"bufferAfterMinutes":0,"allowCustomerResourceSelection":true,"active":true,
                 "requirements":[{"resourceTypeId":%d,"quantity":1,"required":true,"customerCanChoose":true,
                                  "combineResources":true}]}
                """.formatted(tableType), 201);
        serviceId = service.get("id").asLong();
        JsonNode requirement = service.get("requirements").get(0);
        requirementId = requirement.get("id").asLong();
        assertThat(requirement.get("combineResources").asBoolean()).isTrue();
    }

    @Test
    void the_availability_offers_combinations_for_a_group_no_table_seats() throws Exception {
        JsonNode requirement = requirementAtNoon(8);

        assertThat(requirement.get("combinable").asBoolean()).isTrue();
        List<JsonNode> allocations = list(requirement.get("allocations"));
        assertThat(allocations).isNotEmpty().allSatisfy(allocation ->
                assertThat(allocation.get("capacity").asInt()).isGreaterThanOrEqualTo(8));
        // The best option: Table 3 + Table 4, an exact fit.
        assertThat(idsOf(allocations.get(0))).containsExactlyInAnyOrder(table3, table4);
    }

    @Test
    void one_reservation_holds_every_table_of_the_chosen_combination() throws Exception {
        JsonNode created = reserve(8, List.of(table1, table2, table3), "eight@example.com", 201);

        assertThat(created.get("resources")).hasSize(3);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT resource_id FROM reservation_resource", Long.class))
                .containsExactlyInAnyOrder(table1, table2, table3);
    }

    @Test
    void without_a_choice_the_best_combination_is_assigned() throws Exception {
        reserve(8, List.of(), "auto@example.com", 201);

        assertThat(jdbcTemplate.queryForList("SELECT resource_id FROM reservation_resource", Long.class))
                .containsExactlyInAnyOrder(table3, table4);
    }

    @Test
    void a_table_in_one_combination_is_in_no_other_overlapping_one() throws Exception {
        reserve(4, List.of(table1, table2), "first@example.com", 201);

        // Neither table is offered again at that time, alone or combined.
        JsonNode requirement = requirementAtNoon(6);
        assertThat(ids(requirement.get("resources"))).doesNotContain(table1, table2);
        for (JsonNode allocation : requirement.get("allocations")) {
            assertThat(idsOf(allocation)).doesNotContain(table1, table2);
        }
        // And the database-backed check refuses it when asked directly.
        assertThat(reserve(6, List.of(table2, table3), "second@example.com", 409).get("error").asText())
                .isEqualTo("RESOURCE_NOT_AVAILABLE");
        assertThat(reserve(8, List.of(table3, table4), "third@example.com", 201)).isNotNull();
    }

    @Test
    void two_groups_racing_for_overlapping_combinations_cannot_both_win() throws Exception {
        int attempts = 2;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        List<List<Long>> combinations = List.of(List.of(table1, table3), List.of(table2, table3));
        for (int i = 0; i < attempts; i++) {
            String body = body(6, combinations.get(i), "racer" + i + "@example.com");
            String racerToken = customerToken("racer" + i + "@example.com");
            Callable<Integer> attempt = () -> {
                start.await();
                return mockMvc.perform(post("/api/public/businesses/" + slug + "/reservations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(racerToken))
                                .contentType("application/json")
                                .content(body))
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

        // Both want Table 3: exactly one group gets it, and nothing is half-written.
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reservation_resource WHERE resource_id = ?", Integer.class, table3)).isEqualTo(1);
    }

    @Test
    void a_reservation_keeps_its_tables_after_they_are_changed() throws Exception {
        JsonNode created = reserve(8, List.of(table3, table4), "history@example.com", 201);
        String reference = created.get("reference").asText();

        // The business later resizes one table, retires the other and stops combining.
        send(put("/api/admin/resources/" + table3), token, """
                {"resourceTypeId":%d,"name":"Table 3 (round)","capacity":6,"active":true}
                """.formatted(typeOf(table3)), 200);
        send(patch("/api/admin/resources/" + table4 + "/active?value=false"), token, null, 200);

        assertThat(jdbcTemplate.queryForList("SELECT resource_id FROM reservation_resource", Long.class))
                .containsExactlyInAnyOrder(table3, table4);
        JsonNode reservation = send(get("/api/public/reservations/" + reference), null, null, 200);
        assertThat(reservation.get("numberOfParticipants").asInt()).isEqualTo(8);
        assertThat(list(reservation.get("resources")).stream().map(resource -> resource.get("name").asText()))
                .hasSize(2);
    }

    @Test
    void a_combination_with_a_spare_table_is_refused() throws Exception {
        assertThat(reserve(6, List.of(table1, table3, table4), "spare@example.com", 422).get("error").asText())
                .isEqualTo("INVALID_RESOURCE_SELECTION");
    }

    // --- helpers -----------------------------------------------------------

    private long table(long typeId, String name, int capacity) throws Exception {
        return send(post("/api/admin/resources"), token, """
                {"resourceTypeId":%d,"name":"%s","capacity":%d,"active":true}
                """.formatted(typeId, name, capacity), 201).get("id").asLong();
    }

    private long typeOf(long resourceId) {
        return jdbcTemplate.queryForObject("SELECT resource_type_id FROM resource WHERE id = ?", Long.class,
                resourceId);
    }

    private JsonNode reserve(int participants, List<Long> tables, String email, int expectedStatus) throws Exception {
        return send(post("/api/public/businesses/" + slug + "/reservations"), customerToken(email), body(participants, tables, email),
                expectedStatus);
    }

    private String body(int participants, List<Long> tables, String email) {
        String selections = tables.isEmpty() ? "[]" : """
                [{"requirementId":%d,"resourceIds":[%s]}]""".formatted(requirementId,
                tables.stream().map(String::valueOf).collect(Collectors.joining(",")));
        return """
                {"serviceId":%d,"participants":%d,"startTime":"%sT12:00:00","resourceSelections":%s,
                 "customer":{"firstName":"Test","lastName":"Guest","email":"%s","phone":"+389 70 %06d"}}
                """.formatted(serviceId, participants, day, selections, email, Math.abs(email.hashCode() % 1_000_000));
    }

    /** The table requirement as listed at 12:00 for a group; fails when 12:00 is not offered. */
    private JsonNode requirementAtNoon(int participants) throws Exception {
        JsonNode days = send(get("/api/public/businesses/" + slug + "/services/" + serviceId + "/availability?from="
                + day + "&to=" + day + "&participants=" + participants), null, null, 200);
        for (JsonNode slot : days.get(0).get("slots")) {
            if (slot.get("time").asText().startsWith("12:00")) {
                return slot.get("requirements").get(0);
            }
        }
        throw new AssertionError("12:00 is not offered for " + participants);
    }

    private static List<JsonNode> list(JsonNode array) {
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        return items;
    }

    private static List<Long> ids(JsonNode resources) {
        return list(resources).stream().map(resource -> resource.get("id").asLong()).toList();
    }

    private static List<Long> idsOf(JsonNode allocation) {
        return ids(allocation.get("resources"));
    }
}
