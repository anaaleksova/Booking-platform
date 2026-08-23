package com.bookingsaas.analytics;

import com.bookingsaas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reports endpoints: revenue totals, revenue by service and customer
 * spend, plus the two things every owner-only, tenant-scoped report must get
 * right — a staff account is refused, and another business's data never
 * leaks in.
 *
 * <p>Bookings are seeded directly through {@link JdbcTemplate} rather than the
 * dashboard booking API: the availability engine (staff eligibility, working
 * hours, buffers) is already covered elsewhere, and is orthogonal to whether
 * these aggregation queries sum and group correctly. Anchoring every booking
 * at local noon today keeps the numbers stable regardless of what time of day
 * the suite happens to run.
 */
@DisplayName("analytics reports")
class AnalyticsIT extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Skopje");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenA;
    private Long businessId;
    private Long haircutId;
    private Long massageId;
    private Long aliceId;
    private Long bobId;
    private Instant anchor;

    @BeforeEach
    void setUpBusinessWithBookings() throws Exception {
        tokenA = registerAndLogin("owner@example.com", "Reports Co");
        businessId = currentBusinessId(tokenA);

        haircutId = createService(tokenA, "Haircut", "30.00");
        massageId = createService(tokenA, "Massage", "50.00");
        aliceId = createCustomer(tokenA, "Alice", "alice@example.com");
        bobId = createCustomer(tokenA, "Bob", "bob@example.com");

        anchor = LocalDate.now(ZONE).atTime(12, 0).atZone(ZONE).toInstant();

        // Alice: two revenue-generating visits (one completed, one no-show) plus
        // a completed massage — three non-cancelled bookings, no cancellations.
        insertBooking(aliceId, haircutId, "COMPLETED", "30.00", anchor);
        insertBooking(aliceId, massageId, "COMPLETED", "50.00", anchor.plusSeconds(600));
        insertBooking(aliceId, haircutId, "NO_SHOW", "30.00", anchor.plusSeconds(2400));

        // Bob: one upcoming booking, and one he cancelled — spends less than
        // Alice, and is the only one with a cancellation.
        insertBooking(bobId, haircutId, "CONFIRMED", "30.00", anchor.plusSeconds(1200));
        insertBooking(bobId, haircutId, "CANCELLED", "30.00", anchor.plusSeconds(1800));
    }

    @Test
    void overview_totals_match_the_seeded_bookings() throws Exception {
        // 5 bookings total; revenue excludes only the cancelled one:
        // 30 + 50 + 30 (no-show) + 30 (confirmed) = 140, over 4 bookings.
        mockMvc.perform(get("/api/analytics/overview").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueToday").value(140.0))
                .andExpect(jsonPath("$.revenueThisWeek").value(140.0))
                .andExpect(jsonPath("$.revenueThisMonth").value(140.0))
                .andExpect(jsonPath("$.revenueThisYear").value(140.0))
                .andExpect(jsonPath("$.currency").value("MKD"))
                .andExpect(jsonPath("$.appointmentCount").value(5))
                .andExpect(jsonPath("$.completedCount").value(2))
                .andExpect(jsonPath("$.cancelledCount").value(1))
                .andExpect(jsonPath("$.noShowCount").value(1))
                .andExpect(jsonPath("$.averageBookingValue").value(35.0))
                .andExpect(jsonPath("$.cancellationRate").value(0.2))
                .andExpect(jsonPath("$.noShowRate").value(0.2));
    }

    @Test
    void revenue_by_service_ranks_and_sums_correctly() throws Exception {
        mockMvc.perform(get("/api/analytics/revenue-by-service").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Haircut: completed + no-show + confirmed = 3 bookings, 90 revenue.
                .andExpect(jsonPath("$[0].serviceId").value(haircutId))
                .andExpect(jsonPath("$[0].appointmentCount").value(3))
                .andExpect(jsonPath("$[0].revenue").value(90.0))
                .andExpect(jsonPath("$[0].currency").value("MKD"))
                // Massage: one completed booking, 50 revenue.
                .andExpect(jsonPath("$[1].serviceId").value(massageId))
                .andExpect(jsonPath("$[1].appointmentCount").value(1))
                .andExpect(jsonPath("$[1].revenue").value(50.0));
    }

    @Test
    void customers_are_ranked_by_spend_with_favourite_service_and_cancellations() throws Exception {
        mockMvc.perform(get("/api/analytics/customers").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                // Alice: 110 spent over 3 appointments, favourite is Haircut (2 vs 1), no cancellations.
                .andExpect(jsonPath("$.content[0].customerId").value(aliceId))
                .andExpect(jsonPath("$.content[0].totalSpent").value(110.0))
                .andExpect(jsonPath("$.content[0].appointmentCount").value(3))
                .andExpect(jsonPath("$.content[0].averageAppointmentValue").value(36.67))
                .andExpect(jsonPath("$.content[0].favouriteServiceId").value(haircutId))
                .andExpect(jsonPath("$.content[0].cancellationCount").value(0))
                // Bob: 30 spent over 1 appointment, one cancellation.
                .andExpect(jsonPath("$.content[1].customerId").value(bobId))
                .andExpect(jsonPath("$.content[1].totalSpent").value(30.0))
                .andExpect(jsonPath("$.content[1].appointmentCount").value(1))
                .andExpect(jsonPath("$.content[1].cancellationCount").value(1));
    }

    @Test
    void a_staff_account_is_refused() throws Exception {
        String staffToken = createStaffLogin("staff@example.com", businessId);

        mockMvc.perform(get("/api/analytics/overview").header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/analytics/revenue-by-service").header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/analytics/customers").header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void another_businesss_activity_never_appears() throws Exception {
        String tokenB = registerAndLogin("owner-b@example.com", "Other Business");
        createService(tokenB, "Something Else", "999.00");

        mockMvc.perform(get("/api/analytics/overview").header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueToday").value(0))
                .andExpect(jsonPath("$.appointmentCount").value(0));
        mockMvc.perform(get("/api/analytics/customers").header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // --- helpers -------------------------------------------------------

    private Long currentBusinessId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/business").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json(response).get("id").asLong();
    }

    private Long createService(String token, String name, String price) throws Exception {
        String response = mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","durationMinutes":30,"price":%s,"currency":"MKD",
                                 "bufferBeforeMinutes":0,"bufferAfterMinutes":0,
                                 "requiresStaff":false,"requiresResource":false,"active":true}
                                """.formatted(name, price)))
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

    private void insertBooking(Long customerId, Long serviceId, String status, String price, Instant start) {
        Instant end = start.plusSeconds(1800);
        jdbcTemplate.update("""
                        INSERT INTO bookings (business_id, service_id, customer_id, start_date_time, end_date_time,
                                               block_start_date_time, block_end_date_time, status, price, currency,
                                               source)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'MKD', 'DASHBOARD')
                        """,
                businessId, serviceId, customerId, Timestamp.from(start), Timestamp.from(end),
                Timestamp.from(start), Timestamp.from(end), status, new BigDecimal(price));
    }

    /** Adds a STAFF membership on the given business for a freshly created login account. */
    private String createStaffLogin(String email, Long businessId) throws Exception {
        String passwordHash = passwordEncoder.encode("password123");
        Long userId = jdbcTemplate.queryForObject("""
                        INSERT INTO users (email, password_hash, full_name)
                        VALUES (?, ?, 'Staff Member')
                        RETURNING id
                        """, Long.class, email, passwordHash);
        jdbcTemplate.update("""
                        INSERT INTO business_users (business_id, user_id, role)
                        VALUES (?, ?, 'STAFF')
                        """, businessId, userId);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json(response).get("accessToken").asText();
    }
}
