package com.bookingsaas.resource;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** The optional maps of the place, one per floor: saving, validation and what customers see. */
@DisplayName("floor plans")
class FloorPlanIT extends AbstractIntegrationTest {

    private String token;
    private String slug;
    private long tableType;
    private long window;
    private long corner;
    private long serviceId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerAndLogin("owner@bistro.test", "Plan Bistro");
        slug = slugOf(token);
        tableType = send(post("/api/admin/resource-types"), token, """
                {"name":"Table","active":true}""", 201).get("id").asLong();
        window = createTable("Window", 2);
        corner = createTable("Corner", 4);
        serviceId = send(post("/api/admin/services"), token, """
                {"name":"Dinner","durationMinutes":90,"price":0,"pricePerParticipant":false,"maxParticipants":4,
                 "bufferBeforeMinutes":0,"bufferAfterMinutes":0,"allowCustomerResourceSelection":true,"active":true,
                 "requirements":[{"resourceTypeId":%d,"quantity":1,"required":true,"customerCanChoose":true}]}
                """.formatted(tableType), 201).get("id").asLong();
    }

    @Test
    void a_type_has_no_floors_until_one_is_saved() throws Exception {
        assertThat(send(get(plansUrl()), token, null, 200)).isEmpty();
        assertThat(choosable().get("floorPlans")).isEmpty();
        assertThat(typeListing().get("floorPlanCount").asInt()).isZero();
    }

    @Test
    void a_saved_floor_is_shown_to_customers() throws Exception {
        send(put(plansUrl()), token, floors(floor(null, "Ground floor", spot(window, 20, 30, "ROUND"),
                spot(corner, 70, 60, "SQUARE"))), 200);

        JsonNode floor = choosable().get("floorPlans").get(0);
        assertThat(floor.get("name").asText()).isEqualTo("Ground floor");
        assertThat(floor.get("aspectRatio").decimalValue()).isEqualByComparingTo("1.6");
        assertThat(floor.get("spots")).hasSize(2);
        assertThat(floor.get("spots").get(1).get("resourceId").asLong()).isEqualTo(corner);
        assertThat(floor.get("spots").get(1).get("shape").asText()).isEqualTo("SQUARE");
        assertThat(typeListing().get("floorPlanCount").asInt()).isEqualTo(1);
    }

    @Test
    void several_floors_keep_their_order() throws Exception {
        JsonNode saved = send(put(plansUrl()), token, floors(
                floor(null, "Ground floor", spot(window, 20, 30, "ROUND")),
                floor(null, "First floor", spot(corner, 50, 50, "SQUARE"))), 200);

        assertThat(saved).hasSize(2);
        JsonNode shown = choosable().get("floorPlans");
        assertThat(shown.get(0).get("name").asText()).isEqualTo("Ground floor");
        assertThat(shown.get(1).get("name").asText()).isEqualTo("First floor");
        assertThat(typeListing().get("floorPlanCount").asInt()).isEqualTo(2);

        // Reorder by sending the floors the other way round.
        long ground = saved.get(0).get("id").asLong();
        long first = saved.get(1).get("id").asLong();
        send(put(plansUrl()), token, floors(
                floor(first, "First floor", spot(corner, 50, 50, "SQUARE")),
                floor(ground, "Ground floor", spot(window, 20, 30, "ROUND"))), 200);
        assertThat(choosable().get("floorPlans").get(0).get("name").asText()).isEqualTo("First floor");
    }

    @Test
    void a_table_can_move_to_another_floor_in_one_save() throws Exception {
        JsonNode saved = send(put(plansUrl()), token, floors(
                floor(null, "Ground floor", spot(window, 20, 30, "ROUND")),
                floor(null, "First floor")), 200);
        long ground = saved.get(0).get("id").asLong();
        long first = saved.get(1).get("id").asLong();

        JsonNode moved = send(put(plansUrl()), token, floors(
                floor(ground, "Ground floor"),
                floor(first, "First floor", spot(window, 40, 40, "ROUND"))), 200);

        assertThat(moved.get(0).get("items")).isEmpty();
        assertThat(moved.get(1).get("items").get(0).get("resourceId").asLong()).isEqualTo(window);
    }

    @Test
    void floors_left_out_are_deleted() throws Exception {
        JsonNode saved = send(put(plansUrl()), token, floors(
                floor(null, "Ground floor", spot(window, 20, 30, "ROUND")),
                floor(null, "First floor", spot(corner, 50, 50, "SQUARE"))), 200);

        send(put(plansUrl()), token, floors(floor(saved.get(0).get("id").asLong(), "Ground floor",
                spot(window, 20, 30, "ROUND"))), 200);
        assertThat(send(get(plansUrl()), token, null, 200)).hasSize(1);

        send(put(plansUrl()), token, floors(), 200);
        assertThat(choosable().get("floorPlans")).isEmpty();
    }

    @Test
    void the_room_around_the_tables_is_shown_too() throws Exception {
        send(put(plansUrl()), token, """
                {"plans":[{"name":"Ground floor","aspectRatio":1.6,
                 "items":[{"resourceId":%d,"x":20,"y":30,"width":10,"height":16,"shape":"ROUND"}],
                 "decor":[{"kind":"ZONE","label":"Terrace","tone":"GREEN","x":85,"y":50,"width":30,"height":100},
                          {"kind":"PLANT","label":"  ","tone":"PINK","x":5,"y":5,"width":4,"height":6}]}]}
                """.formatted(window), 200);

        JsonNode decor = choosable().get("floorPlans").get(0).get("decor");
        assertThat(decor).hasSize(2);
        assertThat(decor.get(0).get("label").asText()).isEqualTo("Terrace");
        assertThat(decor.get(0).get("tone").asText()).isEqualTo("GREEN");
        // Only areas are tinted; a blank label is no label.
        assertThat(decor.get(1).hasNonNull("tone")).isFalse();
        assertThat(decor.get(1).hasNonNull("label")).isFalse();

        send(put(plansUrl()), token, """
                {"plans":[{"name":"Ground floor","aspectRatio":1.6,"items":[],
                 "decor":[{"kind":"SOFA","x":5,"y":5,"width":4,"height":6}]}]}
                """, 400);
    }

    @Test
    void a_floor_without_tables_is_not_shown_to_customers() throws Exception {
        send(put(plansUrl()), token, floors(
                floor(null, "Ground floor", spot(window, 20, 30, "ROUND")),
                floor(null, "Basement")), 200);

        JsonNode shown = choosable().get("floorPlans");
        assertThat(shown).hasSize(1);
        assertThat(shown.get(0).get("name").asText()).isEqualTo("Ground floor");
    }

    @Test
    void a_table_stands_on_one_floor_only() throws Exception {
        send(put(plansUrl()), token, floors(
                floor(null, "Ground floor", spot(window, 20, 30, "ROUND")),
                floor(null, "First floor", spot(window, 50, 50, "ROUND"))), 400);
        send(put(plansUrl()), token, floors(
                floor(null, "Ground floor", spot(window, 20, 30, "ROUND"), spot(window, 60, 30, "ROUND"))), 400);
    }

    @Test
    void floor_names_are_required_and_distinct() throws Exception {
        send(put(plansUrl()), token, floors(floor(null, "Ground floor"), floor(null, "ground floor")), 400);
        send(put(plansUrl()), token, floors(floor(null, " ")), 400);
    }

    @Test
    void only_resources_of_the_type_can_be_placed() throws Exception {
        long roomType = send(post("/api/admin/resource-types"), token, """
                {"name":"Room","active":true}""", 201).get("id").asLong();
        long room = send(post("/api/admin/resources"), token, """
                {"resourceTypeId":%d,"name":"Room","capacity":1,"active":true}""".formatted(roomType), 201)
                .get("id").asLong();

        send(put(plansUrl()), token, floors(floor(null, "Ground floor", spot(room, 20, 30, "ROUND"))), 400);
    }

    @Test
    void spots_must_stay_on_the_plan() throws Exception {
        send(put(plansUrl()), token, floors(floor(null, "Ground floor", spot(window, 120, 30, "ROUND"))), 400);
    }

    @Test
    void employees_are_not_placed_on_a_map() throws Exception {
        send(put("/api/admin/resource-types/" + employeeTypeId(token) + "/floor-plans"), token,
                floors(floor(null, "Ground floor")), 400);
    }

    @Test
    void another_business_cannot_see_or_change_the_floors() throws Exception {
        JsonNode saved = send(put(plansUrl()), token, floors(floor(null, "Ground floor",
                spot(window, 20, 30, "ROUND"))), 200);
        String other = registerAndLogin("other@bistro.test", "Other Bistro");

        send(get(plansUrl()), other, null, 404);
        send(put(plansUrl()), other, floors(), 404);

        // Nor can its own type adopt this business's floor by id.
        long otherType = send(post("/api/admin/resource-types"), other, """
                {"name":"Table","active":true}""", 201).get("id").asLong();
        send(put("/api/admin/resource-types/" + otherType + "/floor-plans"), other,
                floors(floor(saved.get(0).get("id").asLong(), "Stolen")), 404);
    }

    // --- helpers -----------------------------------------------------------

    private long createTable(String name, int seats) throws Exception {
        return send(post("/api/admin/resources"), token, """
                {"resourceTypeId":%d,"name":"%s","capacity":%d,"active":true}""".formatted(tableType, name, seats), 201)
                .get("id").asLong();
    }

    private String plansUrl() {
        return "/api/admin/resource-types/" + tableType + "/floor-plans";
    }

    private static String floors(String... floors) {
        return "{\"plans\":[" + String.join(",", floors) + "]}";
    }

    private static String floor(Long id, String name, String... spots) {
        return "{" + (id == null ? "" : "\"id\":" + id + ",") + "\"name\":\"" + name
                + "\",\"aspectRatio\":1.6,\"items\":[" + String.join(",", spots) + "]}";
    }

    private static String spot(long resourceId, int x, int y, String shape) {
        return """
                {"resourceId":%d,"x":%d,"y":%d,"width":10,"height":16,"shape":"%s"}""".formatted(resourceId, x, y, shape);
    }

    private JsonNode choosable() throws Exception {
        return send(get("/api/public/businesses/" + slug + "/services/" + serviceId), null, null, 200)
                .get("choosableRequirements").get(0);
    }

    private JsonNode typeListing() throws Exception {
        for (JsonNode type : send(get("/api/admin/resource-types"), token, null, 200)) {
            if (type.get("id").asLong() == tableType) {
                return type;
            }
        }
        throw new AssertionError("table type missing");
    }
}
