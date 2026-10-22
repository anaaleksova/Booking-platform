package com.bookingsaas.common.ratelimit;

import com.bookingsaas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The limits switched on. Every test uses its own client addresses, because
 * the counters live in the limiter for the whole (cached) application context.
 */
@TestPropertySource(properties = "app.rate-limit.enabled=true")
class RateLimitIT extends AbstractIntegrationTest {

    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger(1);

    @Test
    void one_address_gets_twenty_auth_attempts_a_minute() throws Exception {
        String address = freshAddress();
        for (int i = 0; i < 20; i++) {
            assertThat(login(address, "someone" + i + "@example.com", "wrong-password").getStatus()).isEqualTo(401);
        }
        MockHttpServletResponse limited = login(address, "someone@example.com", "wrong-password");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(objectMapper.readTree(limited.getContentAsString()).get("error").asText())
                .isEqualTo("TOO_MANY_REQUESTS");

        // Another address is unaffected.
        assertThat(login(freshAddress(), "someone@example.com", "wrong-password").getStatus()).isEqualTo(401);
    }

    @Test
    void ten_failed_logins_lock_the_account_for_a_while_from_any_address() throws Exception {
        registerAndLogin("target@example.com", "Target Co");
        for (int i = 0; i < 10; i++) {
            assertThat(login(freshAddress(), "target@example.com", "guess-" + i).getStatus()).isEqualTo(401);
        }
        // Now even the right password is refused, from a new address.
        MockHttpServletResponse locked = login(freshAddress(), "target@example.com", "password123");
        assertThat(locked.getStatus()).isEqualTo(429);
    }

    @Test
    void a_successful_login_forgives_earlier_mistakes() throws Exception {
        registerAndLogin("forgetful@example.com", "Forgetful Co");
        for (int i = 0; i < 9; i++) {
            login(freshAddress(), "forgetful@example.com", "guess-" + i);
        }
        assertThat(login(freshAddress(), "forgetful@example.com", "password123").getStatus()).isEqualTo(200);
        for (int i = 0; i < 9; i++) {
            login(freshAddress(), "forgetful@example.com", "guess-" + i);
        }
        assertThat(login(freshAddress(), "forgetful@example.com", "password123").getStatus()).isEqualTo(200);
    }

    @Test
    void availability_is_limited_per_address() throws Exception {
        String address = freshAddress();
        // An unknown business answers 404, but the request still counts.
        String url = "/api/public/businesses/nowhere/services/1/availability?from=2030-01-01";
        for (int i = 0; i < 120; i++) {
            assertThat(perform(get(url), address).getStatus()).isEqualTo(404);
        }
        assertThat(perform(get(url), address).getStatus()).isEqualTo(429);
    }

    @Test
    void browsing_a_business_page_is_not_limited() throws Exception {
        String address = freshAddress();
        for (int i = 0; i < 150; i++) {
            assertThat(perform(get("/api/public/businesses/nowhere"), address).getStatus()).isEqualTo(404);
        }
    }

    private MockHttpServletResponse login(String address, String email, String password) throws Exception {
        return perform(post("/api/auth/login").contentType("application/json").content("""
                {"email":"%s","password":"%s"}""".formatted(email, password)), address);
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String address) throws Exception {
        return mockMvc.perform(request.with(servletRequest -> {
            servletRequest.setRemoteAddr(address);
            return servletRequest;
        })).andReturn().getResponse();
    }

    private static String freshAddress() {
        int n = NEXT_ADDRESS.getAndIncrement();
        return "203.0.113." + n;
    }
}
