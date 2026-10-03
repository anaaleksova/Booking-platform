package com.bookingsaas.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that need a real PostgreSQL.
 *
 * <p>Real Postgres rather than H2 on purpose: the exclusion constraint that
 * prevents double booking is the whole point, and an in-memory database would
 * not enforce it — the tests would pass while production broke.
 *
 * <p>Tagged {@code integration} and skipped automatically when no Docker
 * daemon is reachable, so {@code mvn test} stays runnable anywhere.
 */
@Tag("integration")
@EnabledIf("dockerAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /**
     * One container for the whole test JVM. Deliberately not a JUnit
     * {@code @Container}: that would stop it after the first test class while
     * Spring's cached application context still points at it. Started once
     * here (only when Docker is available); Testcontainers removes it when the
     * JVM exits.
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        if (dockerAvailable()) {
            POSTGRES.start();
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * A clean database per test. The container is shared for speed, so state
     * has to be truncated rather than recreated.
     */
    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE notification, floor_plan_decor, floor_plan_item, floor_plan, gallery_image, media, reservation_resource, reservation, discount_service, discount,
                         blocked_time, resource_service, service_resource_requirement, service,
                         employee, resource_hours, resource, resource_type, customer, business_hours,
                         business_settings, business_user, business, app_user
                RESTART IDENTITY CASCADE
                """);
    }

    /** Registers an owner plus their business and returns the access token. */
    protected String registerAndLogin(String email, String businessName) throws Exception {
        JsonNode response = send(post("/api/auth/register"), null, """
                {"fullName":"Test Owner","email":"%s","password":"password123",
                 "businessName":"%s","timezone":"Europe/Skopje"}
                """.formatted(email, businessName), 201);
        return response.get("accessToken").asText();
    }

    /** Opens the business every day 08:00-20:00 and lets customers book with no notice. */
    protected void openEveryDay(String token) throws Exception {
        StringBuilder week = new StringBuilder("[");
        for (int day = 1; day <= 7; day++) {
            week.append(day > 1 ? "," : "").append("""
                    {"dayOfWeek":%d,"startTime":"08:00","endTime":"20:00"}""".formatted(day));
        }
        send(put("/api/admin/opening-hours"), token, week.append("]").toString(), 200);
        send(put("/api/admin/business/settings"), token, """
                {"minimumBookingNoticeMinutes":0,"maximumBookingAdvanceDays":60,"slotIntervalMinutes":30,
                 "allowCustomerCancellation":true,"cancellationDeadlineHours":0,
                 "emailNotificationsEnabled":false,"reminderNotificationsEnabled":false,"reminderLeadHours":24}
                """, 200);
    }

    protected Long employeeTypeId(String token) throws Exception {
        for (JsonNode type : send(get("/api/admin/resource-types"), token, null, 200)) {
            if (type.get("employeeType").asBoolean()) {
                return type.get("id").asLong();
            }
        }
        throw new AssertionError("business has no employee type");
    }

    protected String slugOf(String token) throws Exception {
        return send(get("/api/admin/business"), token, null, 200).get("slug").asText();
    }

    /** Performs a request, asserts the status and returns the JSON body (or null when empty). */
    protected JsonNode send(MockHttpServletRequestBuilder request, String token, String body, int expectedStatus)
            throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType("application/json").content(body);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? null : objectMapper.readTree(content);
    }

    protected JsonNode json(String payload) throws Exception {
        return objectMapper.readTree(payload);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
