package com.bookingsaas.integration;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A spa set up for integration tests: two rooms, one therapist (Anna, up to
 * two guests at once), a one-person Classic Massage (50) and a Couples
 * Massage for up to two (80), open every day 08:00-20:00, and an integration
 * token with every permission.
 */
abstract class AbstractSpaIntegrationTest extends AbstractIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("Europe/Skopje");

    String owner;
    long businessId;
    String token;
    long classicId;
    long couplesId;
    LocalDate day;

    @BeforeEach
    void setUpSpa() throws Exception {
        owner = registerAndLogin("owner@spa.test", "Serenity Test Spa");
        openEveryDay(owner);
        businessId = send(get("/api/admin/business"), owner, null, 200).get("id").asLong();
        day = LocalDate.now(ZONE).plusDays(1);

        long roomType = send(post("/api/admin/resource-types"), owner, """
                {"name":"Treatment Room","active":true}""", 201).get("id").asLong();
        send(post("/api/admin/resources"), owner, """
                {"resourceTypeId":%d,"name":"Room 1","capacity":2,"active":true}""".formatted(roomType), 201);
        send(post("/api/admin/resources"), owner, """
                {"resourceTypeId":%d,"name":"Room 2","capacity":2,"active":true}""".formatted(roomType), 201);
        send(post("/api/admin/employees"), owner, """
                {"name":"Anna","active":true,"capacity":2}""", 201);
        long employeeType = employeeTypeId(owner);
        classicId = service("Classic Massage", 50, 1, roomType, employeeType);
        couplesId = service("Couples Massage", 80, 2, roomType, employeeType);
        token = createToken(owner, "n8n");
    }

    long service(String name, int price, int maxParticipants, long roomType, long employeeType) throws Exception {
        return send(post("/api/admin/services"), owner, """
                {"name":"%s","durationMinutes":60,"price":%d,"pricePerParticipant":false,"maxParticipants":%d,
                 "bufferBeforeMinutes":0,"bufferAfterMinutes":0,"allowCustomerResourceSelection":false,"active":true,
                 "requirements":[
                   {"resourceTypeId":%d,"quantity":1,"required":true,"customerCanChoose":false},
                   {"resourceTypeId":%d,"quantity":1,"required":true,"customerCanChoose":false}]}
                """.formatted(name, price, maxParticipants, roomType, employeeType), 201).get("id").asLong();
    }

    /** An integration token with every permission. */
    String createToken(String ownerToken, String name) throws Exception {
        return send(post("/api/admin/integrations/tokens"), ownerToken, """
                {"name":"%s","permissions":["READ_BUSINESS","READ_SERVICES","READ_AVAILABILITY","CREATE_RESERVATION",
                 "READ_RESERVATION","CANCEL_RESERVATION","READ_CUSTOMER","CREATE_CUSTOMER","MANAGE_CONVERSATIONS"]}
                """.formatted(name), 201).get("secret").asText();
    }

    /** A reservation for Instagram user ig-{igUser}, with a phone number derived from it. */
    String reservationBody(long serviceId, int participants, String time, String igUser) {
        return """
                {"serviceId":%d,"numberOfParticipants":%d,"startTime":"%sT%s:00",
                 "externalCustomer":{"provider":"INSTAGRAM","externalUserId":"ig-%s","displayName":"ana.p",
                                     "firstName":"Ana","lastName":"Petrova","phone":"+3897012%s"}}
                """.formatted(serviceId, participants, day, time, igUser, igUser);
    }

    JsonNode integration(MockHttpServletRequestBuilder request, String integrationToken, String body,
                         int expectedStatus) throws Exception {
        return send(request, integrationToken, body, expectedStatus);
    }

    String api(String path) {
        return "/api/integrations/businesses/" + businessId + path;
    }

    String slug() throws Exception {
        return slugOf(owner);
    }

    long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
