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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared tables end to end: groups fill the seats of one table at the same
 * time, the database refuses to oversell them even under a race, and tables
 * kept for bigger groups are not given to small ones.
 */
@DisplayName("shared seating")
class SharedSeatingIT extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Skopje");

    private String slug;
    private long serviceId;
    private long requirementId;
    private long longTable;
    private long booth;
    private LocalDate day;

    @BeforeEach
    void setUp() throws Exception {
        String token = registerAndLogin("owner@hall.test", "Beer Hall");
        slug = slugOf(token);
        openEveryDay(token);
        day = LocalDate.now(ZONE).plusDays(1);

        long tableType = send(post("/api/admin/resource-types"), token, """
                {"name":"Table","active":true}""", 201).get("id").asLong();
        longTable = send(post("/api/admin/resources"), token, """
                {"resourceTypeId":%d,"name":"Long table","capacity":8,"sharedSeating":true,"active":true}
                """.formatted(tableType), 201).get("id").asLong();
        booth = send(post("/api/admin/resources"), token, """
                {"resourceTypeId":%d,"name":"Booth","capacity":8,"minPartySize":5,"active":true}
                """.formatted(tableType), 201).get("id").asLong();
        JsonNode service = send(post("/api/admin/services"), token, """
                {"name":"Dinner","durationMinutes":60,"price":0,"pricePerParticipant":false,"maxParticipants":8,
                 "bufferBeforeMinutes":0,"bufferAfterMinutes":0,"allowCustomerResourceSelection":true,"active":true,
                 "requirements":[{"resourceTypeId":%d,"quantity":1,"required":true,"customerCanChoose":true}]}
                """.formatted(tableType), 201);
        serviceId = service.get("id").asLong();
        requirementId = service.get("requirements").get(0).get("id").asLong();
    }

    @Test
    void groups_share_the_table_until_its_seats_are_taken() throws Exception {
        reserve(2, longTable, "first@example.com", 201);

        // Six seats are left for others at the same time.
        JsonNode table = freeTable(6, longTable);
        assertThat(table.get("seatsLeft").asInt()).isEqualTo(6);
        reserve(6, longTable, "second@example.com", 201);

        assertThat(freeTable(1, longTable)).isNull();
        assertThat(reserve(1, longTable, "third@example.com", 409).get("error").asText())
                .isEqualTo("RESOURCE_NOT_AVAILABLE");
    }

    @Test
    void a_shared_table_is_listed_with_its_seat_setting() throws Exception {
        JsonNode options = send(get("/api/public/businesses/" + slug + "/services/" + serviceId), null, null, 200)
                .get("choosableRequirements").get(0).get("resources");
        for (JsonNode option : options) {
            if (option.get("id").asLong() == longTable) {
                assertThat(option.get("shared").asBoolean()).isTrue();
            } else {
                assertThat(option.get("shared").asBoolean()).isFalse();
                assertThat(option.get("minPartySize").asInt()).isEqualTo(5);
            }
        }
    }

    @Test
    void the_database_does_not_oversell_seats_when_groups_race() throws Exception {
        int attempts = 2;
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
                                .content(body(5, longTable, email)))
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

        // 5 + 5 would need ten of eight seats: exactly one group gets in.
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }

    @Test
    void a_table_kept_for_bigger_groups_is_not_given_to_a_small_one() throws Exception {
        assertThat(freeTable(2, booth)).isNull();
        assertThat(reserve(2, booth, "pair@example.com", 422).get("error").asText())
                .isEqualTo("INVALID_RESOURCE_SELECTION");
        reserve(5, booth, "five@example.com", 201);
    }

    @Test
    void a_shared_table_needs_a_capacity() throws Exception {
        String token = registerAndLogin("owner@other-hall.test", "Other Hall");
        long type = send(post("/api/admin/resource-types"), token, """
                {"name":"Bench","active":true}""", 201).get("id").asLong();
        send(post("/api/admin/resources"), token, """
                {"resourceTypeId":%d,"name":"Bench","sharedSeating":true,"active":true}""".formatted(type), 400);
    }

    // --- helpers -----------------------------------------------------------

    private JsonNode reserve(int participants, long tableId, String email, int expectedStatus) throws Exception {
        return send(post("/api/public/businesses/" + slug + "/reservations"), customerToken(email), body(participants, tableId, email),
                expectedStatus);
    }

    private String body(int participants, long tableId, String email) {
        return """
                {"serviceId":%d,"participants":%d,"startTime":"%sT12:00:00",
                 "resourceSelections":[{"requirementId":%d,"resourceIds":[%d]}],
                 "customer":{"firstName":"Test","lastName":"Guest","email":"%s","phone":"+389 70 %06d"}}
                """.formatted(serviceId, participants, day, requirementId, tableId, email,
                Math.abs(email.hashCode() % 1_000_000));
    }

    /** The table as listed free at 12:00 for a group, or null when it is not. */
    private JsonNode freeTable(int participants, long tableId) throws Exception {
        JsonNode days = send(get("/api/public/businesses/" + slug + "/services/" + serviceId + "/availability?from="
                + day + "&to=" + day + "&participants=" + participants), null, null, 200);
        for (JsonNode slot : days.get(0).get("slots")) {
            if (slot.get("time").asText().startsWith("12:00")) {
                for (JsonNode resource : slot.get("requirements").get(0).get("resources")) {
                    if (resource.get("id").asLong() == tableId) {
                        return resource;
                    }
                }
            }
        }
        return null;
    }
}
