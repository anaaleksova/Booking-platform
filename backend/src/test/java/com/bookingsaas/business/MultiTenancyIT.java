package com.bookingsaas.business;

import com.bookingsaas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The mandatory security test: business A must not reach business B's data.
 *
 * <p>Every check here works by taking a real id belonging to B and presenting
 * it with A's token — the exact attack of "change the number in the URL".
 */
@DisplayName("multi-tenant isolation")
class MultiTenancyIT extends AbstractIntegrationTest {

    private String tokenA;
    private String tokenB;
    private Long serviceOfB;
    private Long customerOfB;

    @BeforeEach
    void setUpTwoBusinesses() throws Exception {
        tokenA = registerAndLogin("owner-a@example.com", "Business A");
        tokenB = registerAndLogin("owner-b@example.com", "Business B");

        serviceOfB = createService(tokenB, "B's Service");
        customerOfB = createCustomer(tokenB, "B's Customer", "b.customer@example.com");
    }

    @Test
    void a_service_of_another_business_is_not_found() throws Exception {
        mockMvc.perform(get("/api/services/{id}", serviceOfB).header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void a_customer_of_another_business_is_not_found() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", customerOfB).header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void another_business_cannot_be_edited_through_its_id() throws Exception {
        // There is no /api/businesses/{id} route at all: the tenant comes from
        // the token, so A editing "business 2" is not expressible.
        mockMvc.perform(put("/api/business")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType("application/json")
                        .content("""
                                {"name":"Renamed by A","slug":"renamed-by-a","timezone":"Europe/Skopje"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed by A"));

        mockMvc.perform(get("/api/business").header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Business B"));
    }

    @Test
    void listings_contain_only_the_callers_own_rows() throws Exception {
        createService(tokenA, "A's Service");

        mockMvc.perform(get("/api/services").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("A's Service"));
    }

    @Test
    void staff_cannot_be_assigned_a_service_belonging_to_another_business() throws Exception {
        Long staffOfA = createStaff(tokenA, "A's Staff");

        mockMvc.perform(put("/api/staff/{id}/services", staffOfA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType("application/json")
                        .content("[" + serviceOfB + "]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void a_booking_cannot_be_created_against_another_businesss_service() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType("application/json")
                        .content("""
                                {"serviceId":%d,
                                 "customer":{"name":"Walk in","phone":"+38970000000"},
                                 "startDateTime":"2026-09-10T10:00:00"}
                                """.formatted(serviceOfB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void an_unauthenticated_request_is_rejected() throws Exception {
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // --- helpers -----------------------------------------------------------

    private Long createService(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","durationMinutes":60,"price":500,"currency":"MKD",
                                 "bufferBeforeMinutes":0,"bufferAfterMinutes":0,
                                 "requiresStaff":true,"requiresResource":false,"active":true}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(response).get("id").asLong();
    }

    private Long createStaff(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/staff")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","active":true}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(response).get("id").asLong();
    }

    private Long createCustomer(String token, String name, String email) throws Exception {
        String response = mockMvc.perform(post("/api/customers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","email":"%s"}
                                """.formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(response).get("id").asLong();
    }
}
