package com.bookingsaas.event;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Events on the booking page: shown only inside their window, bookable
 * through a service, and "fully booked" once no group fits any more.
 */
@DisplayName("events")
class EventIT extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Skopje");

    private String token;
    private String slug;
    private long serviceId;
    private LocalDate today;

    /** A bar with one table for four and a free two-hour "Table reservation", open 08:00-20:00. */
    @BeforeEach
    void setUp() throws Exception {
        token = registerAndLogin("owner@bar.test", "Corner Bar");
        slug = slugOf(token);
        openEveryDay(token);
        today = LocalDate.now(ZONE);
        long tableType = send(post("/api/admin/resource-types"), token, """
                {"name":"Table","active":true}""", 201).get("id").asLong();
        send(post("/api/admin/resources"), token, """
                {"resourceTypeId":%d,"name":"Table 1","capacity":4,"active":true}""".formatted(tableType), 201);
        serviceId = send(post("/api/admin/services"), token, """
                {"name":"Table reservation","durationMinutes":120,"price":0,"pricePerParticipant":false,
                 "maxParticipants":4,"bufferBeforeMinutes":0,"bufferAfterMinutes":0,
                 "allowCustomerResourceSelection":false,"active":true,
                 "requirements":[{"resourceTypeId":%d,"quantity":1,"required":true,"customerCanChoose":false}]}
                """.formatted(tableType), 201).get("id").asLong();
    }

    @Test
    void an_event_is_on_the_page_from_a_week_before_until_its_day() throws Exception {
        JsonNode soon = create(event("Jazz night", today.plusDays(3), null));
        JsonNode later = create(event("Wine tasting", today.plusDays(10), null));

        assertThat(soon.get("showFrom").asText()).isEqualTo(today.plusDays(3).minusDays(7).toString());
        assertThat(soon.get("showUntil").asText()).isEqualTo(today.plusDays(3).toString());
        assertThat(soon.get("state").asText()).isEqualTo("SHOWING");
        assertThat(later.get("state").asText()).isEqualTo("UPCOMING");
        assertThat(titles(publicEvents())).containsExactly("Jazz night");
    }

    @Test
    void the_owner_chooses_the_window() throws Exception {
        JsonNode event = create(event("Quiz night", today.plusDays(10), null)
                .replace("\"showFrom\":null", "\"showFrom\":\"" + today + "\""));

        assertThat(event.get("state").asText()).isEqualTo("SHOWING");
        assertThat(titles(publicEvents())).containsExactly("Quiz night");
    }

    @Test
    void an_event_with_a_service_is_bookable_until_no_group_fits() throws Exception {
        LocalDate tomorrow = today.plusDays(1);
        create(event("Live band", tomorrow, serviceId));

        JsonNode open = publicEvents().get(0);
        assertThat(open.get("booking").asText()).isEqualTo("OPEN");
        assertThat(open.get("serviceId").asLong()).isEqualTo(serviceId);
        assertThat(open.get("performer").asText()).isEqualTo("The Quartet");

        // The only table, taken for the whole evening of the event.
        send(post("/api/public/businesses/" + slug + "/reservations"), customerToken("guest@example.com"), """
                {"serviceId":%d,"participants":2,"startTime":"%sT18:00:00",
                 "customer":{"firstName":"Ana","lastName":"Petrova","email":"guest@example.com","phone":"+389 70 123 456"}}
                """.formatted(serviceId, tomorrow), 201);

        assertThat(publicEvents().get(0).get("booking").asText()).isEqualTo("FULL");
    }

    @Test
    void an_event_without_a_service_is_an_announcement() throws Exception {
        create(event("Open mic", today.plusDays(2), null));

        JsonNode event = publicEvents().get(0);
        assertThat(event.get("booking").asText()).isEqualTo("NONE");
        assertThat(event.has("serviceId")).isFalse();
    }

    @Test
    void the_window_cannot_end_after_the_event() throws Exception {
        send(post("/api/admin/events"), token, event("Jazz night", today.plusDays(3), null)
                .replace("\"showUntil\":null", "\"showUntil\":\"" + today.plusDays(4) + "\""), 400);
    }

    @Test
    void editing_and_deleting() throws Exception {
        long id = create(event("Jazz night", today.plusDays(3), null)).get("id").asLong();

        send(put("/api/admin/events/" + id), token, event("Jazz & blues night", today.plusDays(3), serviceId), 200);
        assertThat(titles(publicEvents())).containsExactly("Jazz & blues night");

        send(delete("/api/admin/events/" + id), token, null, 204);
        assertThat(publicEvents()).isEmpty();
        assertThat(send(get("/api/admin/events"), token, null, 200)).isEmpty();
    }

    // --- helpers -----------------------------------------------------------

    /** 18:00-20:00 on the day, default window. */
    private String event(String title, LocalDate date, Long service) {
        return """
                {"title":"%s","description":"A night to remember.","performer":"The Quartet","date":"%s",
                 "startTime":"18:00","endTime":"20:00","showFrom":null,"showUntil":null,"serviceId":%s}
                """.formatted(title, date, service);
    }

    private JsonNode create(String body) throws Exception {
        return send(post("/api/admin/events"), token, body, 201);
    }

    private JsonNode publicEvents() throws Exception {
        return send(get("/api/public/businesses/" + slug + "/events"), null, null, 200);
    }

    private static List<String> titles(JsonNode events) {
        List<String> titles = new ArrayList<>();
        events.forEach(event -> titles.add(event.get("title").asText()));
        return titles;
    }
}
