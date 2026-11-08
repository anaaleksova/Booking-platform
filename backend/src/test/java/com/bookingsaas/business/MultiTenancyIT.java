package com.bookingsaas.business;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The mandatory security test: business A must not reach business B's data.
 *
 * <p>Every check takes a real id belonging to B and presents it with A's
 * token — the exact attack of "change the number in the URL".
 */
@DisplayName("multi-tenant isolation")
class MultiTenancyIT extends AbstractIntegrationTest {

    private String tokenA;
    private String tokenB;
    private Long serviceOfB;
    private Long customerOfB;
    private Long resourceTypeOfB;

    @BeforeEach
    void setUpTwoBusinesses() throws Exception {
        tokenA = registerAndLogin("owner-a@example.com", "Business A");
        tokenB = registerAndLogin("owner-b@example.com", "Business B");

        serviceOfB = createService(tokenB, "B's Service", "[]");
        customerOfB = send(post("/api/admin/customers"), tokenB, """
                {"firstName":"B's Customer","email":"b.customer@example.com"}""", 201).get("id").asLong();
        resourceTypeOfB = send(post("/api/admin/resource-types"), tokenB, """
                {"name":"Room","active":true}""", 201).get("id").asLong();
    }

    @Test
    void a_service_of_another_business_is_not_found() throws Exception {
        send(get("/api/admin/services/" + serviceOfB), tokenA, null, 404);
    }

    @Test
    void a_customer_of_another_business_is_not_found() throws Exception {
        send(get("/api/admin/customers/" + customerOfB), tokenA, null, 404);
        send(get("/api/admin/customers/" + customerOfB + "/reservations"), tokenA, null, 404);
    }

    @Test
    void another_business_cannot_be_edited_through_its_id() throws Exception {
        // There is no /api/admin/businesses/{id} route: the tenant comes from
        // the token, so A editing "business 2" is not expressible.
        send(put("/api/admin/business"), tokenA, """
                {"name":"Renamed by A","slug":"renamed-by-a","currency":"EUR","timezone":"Europe/Skopje"}
                """, 200);

        assertThat(send(get("/api/admin/business"), tokenB, null, 200).get("name").asText()).isEqualTo("Business B");
    }

    @Test
    void listings_contain_only_the_callers_own_rows() throws Exception {
        createService(tokenA, "A's Service", "[]");

        var services = send(get("/api/admin/services"), tokenA, null, 200);
        assertThat(services).hasSize(1);
        assertThat(services.get(0).get("name").asText()).isEqualTo("A's Service");
    }

    @Test
    void a_service_cannot_require_another_businesss_resource_type() throws Exception {
        send(post("/api/admin/services"), tokenA, serviceBody("Sneaky", """
                [{"resourceTypeId":%d,"quantity":1,"required":true,"customerCanChoose":false}]
                """.formatted(resourceTypeOfB)), 400);
    }

    @Test
    void an_employee_cannot_be_restricted_to_another_businesss_service() throws Exception {
        send(post("/api/admin/employees"), tokenA, """
                {"name":"A's Employee","active":true,"serviceIds":[%d]}""".formatted(serviceOfB), 400);
    }

    @Test
    void a_reservation_cannot_be_created_against_another_businesss_service() throws Exception {
        send(post("/api/admin/reservations"), tokenA, """
                {"serviceId":%d,"participants":1,"startTime":"2026-12-10T10:00:00",
                 "customer":{"firstName":"Walk in","email":"walk.in@example.com","phone":"+38970000000"}}
                """.formatted(serviceOfB), 404);
    }

    @Test
    void a_homepage_cannot_show_another_businesss_services_people_or_images() throws Exception {
        Long ownService = createService(tokenA, "A's Service", "[]");
        long employeeOfB = send(post("/api/admin/employees"), tokenB, """
                {"name":"B's Employee","active":true}""", 201).get("id").asLong();
        String imageOfB = uploadImage(tokenB);

        JsonNode site = send(get("/api/admin/site"), tokenA, null, 200);
        ObjectNode draft = (ObjectNode) site.get("draft");
        ArrayNode sections = (ArrayNode) draft.get("sections");
        ObjectNode services = section(sections.addObject(), "SERVICES", "GRID");
        services.putObject("content").put("source", "SELECTED").put("showBookButton", true)
                .putArray("serviceIds").add(serviceOfB).add(ownService);
        ObjectNode team = section(sections.addObject(), "TEAM", "CARDS");
        team.putObject("content").putArray("members").addObject().put("employeeId", employeeOfB);
        ObjectNode gallery = section(sections.addObject(), "GALLERY", "GRID");
        gallery.putObject("content").put("source", "CUSTOM").putArray("images").addObject().put("mediaId", imageOfB);
        ObjectNode body = objectMapper.createObjectNode().put("draftVersion", site.get("draftVersion").asInt());
        body.set("document", draft);

        JsonNode saved = send(put("/api/admin/site/draft"), tokenA, body.toString(), 200).get("draft");
        send(post("/api/admin/site/publish"), tokenA, null, 200);

        JsonNode stored = saved.get("sections");
        int last = stored.size() - 1;
        assertThat(stored.get(last - 2).at("/content/serviceIds").toString()).isEqualTo("[" + ownService + "]");
        assertThat(stored.get(last - 1).at("/content/members")).isEmpty();
        assertThat(stored.get(last).at("/content/images")).isEmpty();
        assertThat(send(get("/api/public/businesses/" + slugOf(tokenA) + "/site"), null, null, 200).get("team"))
                .isEmpty();
    }

    @Test
    void one_businesss_homepage_draft_never_reaches_another() throws Exception {
        JsonNode site = send(get("/api/admin/site"), tokenA, null, 200);
        ObjectNode draft = (ObjectNode) site.get("draft");
        ((ObjectNode) draft.at("/theme/colors")).put("primary", "#ff0000");
        ObjectNode body = objectMapper.createObjectNode().put("draftVersion", site.get("draftVersion").asInt());
        body.set("document", draft);
        send(put("/api/admin/site/draft"), tokenA, body.toString(), 200);
        send(post("/api/admin/site/publish"), tokenA, null, 200);

        // There is no business id to aim at: B's own builder and B's public page are untouched.
        assertThat(send(get("/api/admin/site"), tokenB, null, 200).at("/draft/theme/colors/primary").asText())
                .isEqualTo("#2f5bea");
        assertThat(send(get("/api/public/businesses/" + slugOf(tokenB) + "/site"), null, null, 200)
                .at("/site/theme/colors/primary").asText()).isEqualTo("#2f5bea");
    }

    @Test
    void an_unauthenticated_request_is_rejected() throws Exception {
        assertThat(send(get("/api/admin/services"), null, null, 401).get("error").asText())
                .isEqualTo("UNAUTHENTICATED");
    }

    // --- helpers -----------------------------------------------------------

    private static ObjectNode section(ObjectNode section, String type, String layout) {
        section.put("type", type).put("id", UUID.randomUUID().toString()).put("layout", layout);
        section.putObject("visibility").put("desktop", true).put("mobile", true);
        section.putObject("style").put("tone", "DEFAULT");
        return section;
    }

    private Long createService(String token, String name, String requirements) throws Exception {
        return send(post("/api/admin/services"), token, serviceBody(name, requirements), 201).get("id").asLong();
    }

    private static String serviceBody(String name, String requirements) {
        return """
                {"name":"%s","durationMinutes":60,"price":50,"pricePerParticipant":false,"maxParticipants":1,
                 "bufferBeforeMinutes":0,"bufferAfterMinutes":0,"allowCustomerResourceSelection":false,
                 "active":true,"requirements":%s}
                """.formatted(name, requirements);
    }
}
