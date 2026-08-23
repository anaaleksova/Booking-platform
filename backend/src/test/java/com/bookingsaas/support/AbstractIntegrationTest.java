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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that need a real PostgreSQL.
 *
 * <p>Real Postgres rather than H2 on purpose: the exclusion constraints that
 * prevent double booking are the whole point, and an in-memory database would
 * not enforce them — the tests would pass while production broke.
 *
 * <p>Tagged {@code integration} and skipped automatically when no Docker
 * daemon is reachable, so {@code mvn test} stays runnable anywhere.
 */
@Tag("integration")
@EnabledIf("dockerAvailable")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                TRUNCATE notifications, bookings, blocked_times, staff_working_hours, working_hours,
                         staff_services, resource_services, customers, staff, resources, services,
                         business_settings, business_users, businesses, users
                RESTART IDENTITY CASCADE
                """);
    }

    /** Registers an owner plus their business and returns the access token. */
    protected String registerAndLogin(String email, String businessName) throws Exception {
        String body = """
                {"fullName":"Test Owner","email":"%s","password":"password123",
                 "businessName":"%s","timezone":"Europe/Skopje"}
                """.formatted(email, businessName);

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return json(response).get("accessToken").asText();
    }

    protected JsonNode json(String payload) throws Exception {
        return objectMapper.readTree(payload);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
