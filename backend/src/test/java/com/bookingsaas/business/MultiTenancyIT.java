package com.bookingsaas.business;

import com.bookingsaas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    void an_unauthenticated_request_is_rejected() throws Exception {
        assertThat(send(get("/api/admin/services"), null, null, 401).get("error").asText())
                .isEqualTo("UNAUTHENTICATED");
    }

    // --- helpers -----------------------------------------------------------

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
