package com.bookingsaas.analytics;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Revenue and analytics against real data, plus the two things every
 * owner-only, tenant-scoped report must get right — a staff account is
 * refused, and another business's data never leaks in.
 *
 * <p>Reservations are inserted directly: the engine is covered elsewhere and
 * is orthogonal to whether these queries sum and group correctly. Anchoring
 * everything at local noon today keeps the numbers stable whatever time the
 * suite runs.
 */
@DisplayName("analytics")
class AnalyticsIT extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Skopje");

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;
    private Long businessId;
    private Long massageId;
    private Long facialId;
    private LocalDate today;

    @BeforeEach
    void setUpReservations() throws Exception {
        token = registerAndLogin("owner@example.com", "Reports Co");
        businessId = send(get("/api/admin/business"), token, null, 200).get("id").asLong();
        massageId = createService("Massage", "50.00");
        facialId = createService("Facial", "30.00");
        Long alice = createCustomer("Alice", "alice@example.com");
        Long bob = createCustomer("Bob", "bob@example.com");

        today = LocalDate.now(ZONE);
        Instant noon = today.atTime(12, 0).atZone(ZONE).toInstant();

        // Counted: CONFIRMED and COMPLETED.
        insert(alice, massageId, "COMPLETED", "50.00", "0.00", noon);
        insert(alice, massageId, "CONFIRMED", "50.00", "10.00", noon.plusSeconds(3600));
        insert(bob, facialId, "COMPLETED", "30.00", "0.00", noon.plusSeconds(7200));
        // Not counted: PENDING and CANCELLED.
        insert(bob, facialId, "PENDING", "30.00", "0.00", noon.plusSeconds(10800));
        insert(bob, massageId, "CANCELLED", "50.00", "0.00", noon.plusSeconds(14400));
    }

    @Test
    void revenue_counts_only_confirmed_and_completed_reservations() throws Exception {
        JsonNode summary = send(get("/api/admin/analytics/revenue?from=" + today + "&to=" + today), token, null, 200)
                .get("summary");

        assertThat(summary.get("reservations").asLong()).isEqualTo(3);
        assertThat(summary.get("grossRevenue").decimalValue()).isEqualByComparingTo("130");
        assertThat(summary.get("discounts").decimalValue()).isEqualByComparingTo("10");
        assertThat(summary.get("netRevenue").decimalValue()).isEqualByComparingTo("120");
        assertThat(summary.get("averageReservationValue").decimalValue()).isEqualByComparingTo("40");
    }

    @Test
    void the_revenue_series_has_no_gaps() throws Exception {
        JsonNode series = send(get("/api/admin/analytics/revenue?from=" + today.minusDays(6) + "&to=" + today),
                token, null, 200).get("series");

        assertThat(series).hasSize(7);
        assertThat(series.get(6).get("netRevenue").decimalValue()).isEqualByComparingTo("120");
        assertThat(series.get(0).get("netRevenue").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void the_dashboard_and_periods_agree() throws Exception {
        JsonNode dashboard = send(get("/api/admin/dashboard"), token, null, 200);
        assertThat(dashboard.get("todayRevenue").decimalValue()).isEqualByComparingTo("120");
        assertThat(dashboard.get("todayReservations").asLong()).isEqualTo(4);   // non-cancelled
        assertThat(dashboard.get("totalCustomers").asLong()).isEqualTo(2);

        JsonNode periods = send(get("/api/admin/analytics/revenue/periods"), token, null, 200);
        assertThat(periods.get("today").get("netRevenue").decimalValue()).isEqualByComparingTo("120");
        assertThat(periods.get("thisMonth").get("netRevenue").decimalValue()).isEqualByComparingTo("120");
    }

    @Test
    void services_are_ranked_by_reservations_with_their_revenue() throws Exception {
        JsonNode services = send(get("/api/admin/analytics/services?from=" + today + "&to=" + today),
                token, null, 200);

        assertThat(services).hasSize(2);
        assertThat(services.get(0).get("serviceId").asLong()).isEqualTo(massageId);
        assertThat(services.get(0).get("reservations").asLong()).isEqualTo(2);
        assertThat(services.get(0).get("revenue").decimalValue()).isEqualByComparingTo("90");
        assertThat(services.get(1).get("reservations").asLong()).isEqualTo(2);     // completed + pending
        assertThat(services.get(1).get("revenue").decimalValue()).isEqualByComparingTo("30");
    }

    @Test
    void customers_are_split_into_new_and_returning() throws Exception {
        JsonNode report = send(get("/api/admin/analytics/customers?from=" + today + "&to=" + today),
                token, null, 200);

        assertThat(report.get("activeCustomers").asLong()).isEqualTo(2);
        assertThat(report.get("newCustomers").asLong()).isEqualTo(2);
        assertThat(report.get("topCustomers").get(0).get("name").asText()).isEqualTo("Alice");
    }

    @Test
    void a_staff_account_is_refused() throws Exception {
        String staffToken = createStaffLogin("staff@example.com");

        send(get("/api/admin/analytics/revenue"), staffToken, null, 403);
        send(get("/api/admin/dashboard"), staffToken, null, 403);
    }

    @Test
    void another_businesss_activity_never_appears() throws Exception {
        String tokenB = registerAndLogin("owner-b@example.com", "Other Business");

        JsonNode summary = send(get("/api/admin/analytics/revenue"), tokenB, null, 200).get("summary");
        assertThat(summary.get("reservations").asLong()).isZero();
        assertThat(send(get("/api/admin/analytics/services"), tokenB, null, 200)).isEmpty();
    }

    // --- helpers -------------------------------------------------------

    private Long createService(String name, String price) throws Exception {
        return send(post("/api/admin/services"), token, """
                {"name":"%s","durationMinutes":30,"price":%s,"pricePerParticipant":false,"maxParticipants":1,
                 "bufferBeforeMinutes":0,"bufferAfterMinutes":0,"allowCustomerResourceSelection":false,
                 "active":true,"requirements":[]}
                """.formatted(name, price), 201).get("id").asLong();
    }

    private Long createCustomer(String firstName, String email) throws Exception {
        return send(post("/api/admin/customers"), token, """
                {"firstName":"%s","email":"%s"}""".formatted(firstName, email), 201).get("id").asLong();
    }

    private void insert(Long customerId, Long serviceId, String status, String price, String discount, Instant start) {
        Instant end = start.plusSeconds(1800);
        BigDecimal original = new BigDecimal(price);
        BigDecimal off = new BigDecimal(discount);
        jdbcTemplate.update("""
                        INSERT INTO reservation (business_id, customer_id, service_id, start_time, end_time,
                                                 block_start_time, block_end_time, number_of_participants, status,
                                                 original_price, discount_amount, final_price, currency, source)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, 'EUR', 'ADMIN')
                        """,
                businessId, customerId, serviceId, Timestamp.from(start), Timestamp.from(end),
                Timestamp.from(start), Timestamp.from(end), status, original, off, original.subtract(off));
    }

    /** Adds a STAFF membership on the business for a freshly created login account. */
    private String createStaffLogin(String email) throws Exception {
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (email, password_hash, full_name) VALUES (?, ?, 'Staff Member') RETURNING id
                """, Long.class, email, passwordEncoder.encode("password123"));
        jdbcTemplate.update("INSERT INTO business_user (business_id, user_id, role) VALUES (?, ?, 'STAFF')",
                businessId, userId);
        return send(post("/api/auth/login"), null, """
                {"email":"%s","password":"password123"}""".formatted(email), 200).get("accessToken").asText();
    }
}
