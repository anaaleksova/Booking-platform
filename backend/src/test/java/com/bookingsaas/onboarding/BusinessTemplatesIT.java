package com.bookingsaas.onboarding;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** The type picked at sign-up gives the business a working setup, bookable at once. */
class BusinessTemplatesIT extends AbstractIntegrationTest {

    @Test
    void a_restaurant_books_tables_with_one_free_reservation() throws Exception {
        String token = signUp("RESTAURANT", "EUR");

        assertThat(names(send(get("/api/admin/resource-types"), token, null, 200)))
                .containsExactlyInAnyOrder("Employee", "Table");
        JsonNode tables = send(get("/api/admin/resources"), token, null, 200);
        assertThat(tables).hasSize(6);
        assertThat(capacities(tables)).containsExactlyInAnyOrder(2, 2, 4, 4, 4, 6);

        JsonNode services = send(get("/api/admin/services"), token, null, 200);
        assertThat(names(services)).containsExactly("Table reservation");
        JsonNode reservation = services.get(0);
        assertThat(reservation.get("price").decimalValue()).isZero();
        assertThat(reservation.get("maxParticipants").asInt()).isEqualTo(12);
        JsonNode table = reservation.get("requirements").get(0);
        assertThat(table.get("customerCanChoose").asBoolean()).isTrue();
        assertThat(table.get("combineResources").asBoolean()).isTrue();

        assertThat(send(get("/api/admin/opening-hours"), token, null, 200)).hasSize(7);
        assertThat(slotInterval(token)).isEqualTo(30);
        assertThat(send(get("/api/admin/employees"), token, null, 200)).isEmpty();

        bookFirstFreeSlot(token, reservation.get("id").asLong(), 8);   // two tables put together
    }

    @Test
    void a_spa_has_priced_treatments_with_the_owner_and_a_room() throws Exception {
        String token = signUp("SPA", "EUR");

        JsonNode employees = send(get("/api/admin/employees"), token, null, 200);
        assertThat(names(employees)).containsExactly("Ana Owner");
        assertThat(names(send(get("/api/admin/resource-types"), token, null, 200)))
                .containsExactlyInAnyOrder("Employee", "Treatment room");

        JsonNode services = send(get("/api/admin/services"), token, null, 200);
        assertThat(names(services)).containsExactlyInAnyOrder("Classic massage", "Deep tissue massage", "Facial");
        for (JsonNode service : services) {
            assertThat(service.get("price").decimalValue()).isPositive();
            assertThat(service.get("bufferAfterMinutes").asInt()).isEqualTo(10);
            assertThat(service.get("requirements")).hasSize(2);
        }
        assertThat(send(get("/api/admin/opening-hours"), token, null, 200)).hasSize(6);   // Mon–Sat

        bookFirstFreeSlot(token, services.get(0).get("id").asLong(), 1);
    }

    @Test
    void salons_are_split_into_hair_and_nails_and_keep_the_neutral_employee_name() throws Exception {
        String hair = signUp("HAIR_SALON", "EUR");
        assertThat(names(send(get("/api/admin/services"), hair, null, 200)))
                .containsExactlyInAnyOrder("Haircut", "Haircut & beard", "Colouring");
        assertThat(names(send(get("/api/admin/resource-types"), hair, null, 200))).containsExactly("Employee");
        assertThat(slotInterval(hair)).isEqualTo(15);

        String nails = signUp("nails@example.com", "NAILS_BEAUTY", "EUR");
        assertThat(names(send(get("/api/admin/services"), nails, null, 200)))
                .containsExactlyInAnyOrder("Manicure", "Pedicure", "Gel nails");
    }

    @Test
    void fitness_classes_share_a_studio_and_start_on_the_hour() throws Exception {
        String token = signUp("FITNESS", "EUR");

        JsonNode studio = send(get("/api/admin/resources"), token, null, 200).get(0);
        assertThat(studio.get("name").asText()).isEqualTo("Main studio");
        assertThat(studio.get("capacity").asInt()).isEqualTo(12);
        assertThat(studio.get("sharedSeating").asBoolean()).isTrue();
        JsonNode service = send(get("/api/admin/services"), token, null, 200).get(0);
        assertThat(service.get("pricePerParticipant").asBoolean()).isTrue();
        assertThat(slotInterval(token)).isEqualTo(60);
        assertThat(send(get("/api/admin/opening-hours"), token, null, 200)).hasSize(6);   // Mon–Fri + Sat
    }

    @Test
    void a_coworking_space_has_desks_and_a_meeting_room() throws Exception {
        String token = signUp("COWORKING", "EUR");

        assertThat(names(send(get("/api/admin/resource-types"), token, null, 200)))
                .containsExactlyInAnyOrder("Employee", "Desk", "Meeting room");
        assertThat(send(get("/api/admin/resources"), token, null, 200)).hasSize(9);
        assertThat(names(send(get("/api/admin/services"), token, null, 200)))
                .containsExactlyInAnyOrder("Desk, half day", "Desk, full day", "Meeting room, 1 hour");
    }

    @Test
    void sample_prices_follow_the_currency() throws Exception {
        String token = signUp("HAIR_SALON", "MKD");

        assertThat(send(get("/api/admin/business"), token, null, 200).get("currency").asText()).isEqualTo("MKD");
        for (JsonNode service : send(get("/api/admin/services"), token, null, 200)) {
            if (service.get("name").asText().equals("Haircut")) {
                assertThat(service.get("price").decimalValue()).isEqualByComparingTo("900");   // 15 EUR
            }
        }
    }

    @Test
    void something_else_and_old_clients_start_empty_as_before() throws Exception {
        String other = signUp("OTHER", null);
        String old = registerAndLogin("old.client@example.com", "Old Client");   // sends no type

        for (String token : List.of(other, old)) {
            assertThat(send(get("/api/admin/services"), token, null, 200)).isEmpty();
            assertThat(send(get("/api/admin/resources"), token, null, 200)).isEmpty();
            assertThat(send(get("/api/admin/opening-hours"), token, null, 200)).hasSize(5);   // Mon–Fri
            assertThat(send(get("/api/admin/business"), token, null, 200).get("businessType").asText())
                    .isEqualTo("OTHER");
        }
    }

    // --- helpers -----------------------------------------------------------

    private String signUp(String type, String currency) throws Exception {
        return signUp("owner@example.com", type, currency);
    }

    private String signUp(String email, String type, String currency) throws Exception {
        send(post("/api/auth/register"), null, """
                {"fullName":"Ana Owner","email":"%s","password":"password123","businessName":"%s",
                 "timezone":"Europe/Skopje","businessType":"%s"%s}
                """.formatted(email, "New " + type, type,
                currency == null ? "" : ",\"currency\":\"" + currency + "\""), 202);
        send(post("/api/auth/register/verify"), null, """
                {"email":"%s","code":"%s"}""".formatted(email, sentCodes.get(email)), 201);
        return lastSessionToken();
    }

    /** A customer books the first time offered on a Tuesday, which every template opens. */
    private void bookFirstFreeSlot(String token, long serviceId, int guests) throws Exception {
        String slug = slugOf(token);
        LocalDate tuesday = LocalDate.now(ZoneId.of("Europe/Skopje"))
                .plusDays(2).with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        JsonNode days = send(get("/api/public/businesses/" + slug + "/services/" + serviceId
                + "/availability?from=" + tuesday + "&participants=" + guests), null, null, 200);
        JsonNode slot = days.get(0).get("slots").get(0);
        assertThat(slot).as("a free slot on " + tuesday).isNotNull();

        send(post("/api/public/businesses/" + slug + "/reservations"), customerToken("guest@example.com"), """
                {"serviceId":%d,"participants":%d,"startTime":"%sT%s",
                 "customer":{"firstName":"Mila","lastName":"Petrova","email":"guest@example.com","phone":"+389 70 123 456"}}
                """.formatted(serviceId, guests, tuesday, slot.get("time").asText()), 201);
    }

    private int slotInterval(String token) throws Exception {
        return send(get("/api/admin/business/settings"), token, null, 200).get("slotIntervalMinutes").asInt();
    }

    private static List<String> names(JsonNode items) {
        List<String> names = new ArrayList<>();
        items.forEach(item -> names.add(item.get("name").asText()));
        return names;
    }

    private static List<Integer> capacities(JsonNode items) {
        List<Integer> capacities = new ArrayList<>();
        items.forEach(item -> capacities.add(item.get("capacity").asInt()));
        return capacities;
    }
}
