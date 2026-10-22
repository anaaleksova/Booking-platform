package com.bookingsaas.auth;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** The browser session: an httpOnly cookie, guarded against cross-site writes. */
class SessionCookieIT extends AbstractIntegrationTest {

    private static final String OWNER = "owner@cookie.test";

    @Test
    void signing_in_sets_an_httponly_cookie_and_never_returns_the_token() throws Exception {
        registerAndLogin(OWNER, "Cookie Co");

        MockHttpServletResponse response = perform(post("/api/auth/login")
                .contentType("application/json").content(credentials()));

        assertThat(response.getStatus()).isEqualTo(200);
        String cookie = response.getHeader("Set-Cookie");
        assertThat(cookie).startsWith(SESSION_COOKIE + "=").contains("HttpOnly", "SameSite=Lax", "Path=/api");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.has("accessToken")).isFalse();
        assertThat(body.get("user").get("email").asText()).isEqualTo(OWNER);
    }

    @Test
    void the_cookie_alone_authenticates_reads() throws Exception {
        String token = registerAndLogin(OWNER, "Cookie Co");

        MockHttpServletResponse me = perform(get("/api/auth/me").cookie(session(token)));
        assertThat(me.getStatus()).isEqualTo(200);

        MockHttpServletResponse current = perform(get("/api/auth/session").cookie(session(token)));
        assertThat(objectMapper.readTree(current.getContentAsString()).get("email").asText()).isEqualTo(OWNER);
    }

    @Test
    void the_session_endpoint_answers_204_for_nobody() throws Exception {
        assertThat(perform(get("/api/auth/session")).getStatus()).isEqualTo(204);
    }

    @Test
    void a_write_with_the_cookie_needs_the_requested_with_header() throws Exception {
        String token = registerAndLogin(OWNER, "Cookie Co");
        String settings = """
                {"minimumBookingNoticeMinutes":0,"maximumBookingAdvanceDays":60,"slotIntervalMinutes":30,
                 "allowCustomerCancellation":true,"cancellationDeadlineHours":0,
                 "emailNotificationsEnabled":false,"reminderNotificationsEnabled":false,"reminderLeadHours":24}""";

        // What a forged cross-site form would look like: the cookie, but no custom header.
        MockHttpServletResponse forged = perform(put("/api/admin/business/settings").cookie(session(token))
                .contentType("application/json").content(settings));
        assertThat(forged.getStatus()).isEqualTo(403);

        MockHttpServletResponse ours = perform(put("/api/admin/business/settings").cookie(session(token))
                .header(SessionCookies.CSRF_HEADER, "XMLHttpRequest")
                .contentType("application/json").content(settings));
        assertThat(ours.getStatus()).isEqualTo(200);
    }

    @Test
    void a_dead_cookie_is_cleared_and_does_not_block_signing_in() throws Exception {
        registerAndLogin(OWNER, "Cookie Co");

        MockHttpServletResponse response = perform(post("/api/auth/login").cookie(session("not-a-real-token"))
                .header(SessionCookies.CSRF_HEADER, "XMLHttpRequest")
                .contentType("application/json").content(credentials()));

        assertThat(response.getStatus()).isEqualTo(200);
        // First the dead cookie is cleared, then the new session is set.
        assertThat(response.getHeaders("Set-Cookie")).hasSize(2);
        assertThat(response.getHeaders("Set-Cookie").get(0)).contains("Max-Age=0");
        assertThat(response.getHeaders("Set-Cookie").get(1)).doesNotContain("Max-Age=0");
    }

    @Test
    void a_dead_cookie_on_a_protected_endpoint_answers_401() throws Exception {
        MockHttpServletResponse response = perform(get("/api/admin/business").cookie(session("expired")));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    void logging_out_clears_the_cookie() throws Exception {
        MockHttpServletResponse response = perform(post("/api/auth/logout")
                .header(SessionCookies.CSRF_HEADER, "XMLHttpRequest"));
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getHeader("Set-Cookie")).startsWith(SESSION_COOKIE + "=;").contains("Max-Age=0");
    }

    @Test
    void api_responses_carry_security_headers() throws Exception {
        MockHttpServletResponse response = perform(get("/api/auth/session"));
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private static Cookie session(String token) {
        return new Cookie(SESSION_COOKIE, token);
    }

    private static String credentials() {
        return """
                {"email":"%s","password":"password123"}""".formatted(OWNER);
    }
}
