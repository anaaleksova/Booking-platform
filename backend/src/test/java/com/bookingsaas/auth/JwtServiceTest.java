package com.bookingsaas.auth;

import com.bookingsaas.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The example secrets from the repository are refused anywhere but in development and tests. */
class JwtServiceTest {

    private static final String PLACEHOLDER = "local-development-only-secret-please-change-me";
    private static final String REAL = "k3Jv9qXbR2mT8wYzA5nC7eF1hL4pS6uD0gI3oQ9rV2x";

    @Test
    void a_placeholder_secret_stops_a_production_start() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtService(properties(PLACEHOLDER), production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void a_placeholder_secret_is_fine_in_development() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        assertThat(new JwtService(properties(PLACEHOLDER), dev).issue(1L, "a@b.c", null, "hash")).isNotBlank();
    }

    @Test
    void a_real_secret_works_everywhere() {
        MockEnvironment production = new MockEnvironment();
        JwtService service = new JwtService(properties(REAL), production);

        String token = service.issue(7L, "a@b.c", 3L, "hash");
        assertThat(service.parse(token).getPayload().getSubject()).isEqualTo("7");
    }

    private static AppProperties properties(String secret) {
        return new AppProperties(new AppProperties.Jwt(secret, 60, "booking-platform"), null,
                "http://localhost", null, null, null);
    }
}
