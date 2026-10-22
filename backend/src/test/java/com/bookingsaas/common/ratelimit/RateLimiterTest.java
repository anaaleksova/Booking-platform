package com.bookingsaas.common.ratelimit;

import com.bookingsaas.common.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock, properties(true));

    @Test
    void allows_up_to_the_limit_then_refuses() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", 3, MINUTE)).isTrue();
        }
        assertThat(limiter.tryAcquire("k", 3, MINUTE)).isFalse();
        assertThat(limiter.secondsUntilReset("k")).isBetween(1L, 60L);
    }

    @Test
    void a_new_window_starts_fresh() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("k", 3, MINUTE);
        }
        clock.advance(Duration.ofSeconds(61));
        assertThat(limiter.tryAcquire("k", 3, MINUTE)).isTrue();
    }

    @Test
    void keys_are_counted_separately() {
        limiter.tryAcquire("a", 1, MINUTE);
        assertThat(limiter.tryAcquire("a", 1, MINUTE)).isFalse();
        assertThat(limiter.tryAcquire("b", 1, MINUTE)).isTrue();
    }

    @Test
    void failures_exhaust_a_key_until_reset() {
        for (int i = 0; i < 2; i++) {
            limiter.hit("login", MINUTE);
        }
        assertThat(limiter.isExhausted("login", 2, MINUTE)).isTrue();
        limiter.reset("login");
        assertThat(limiter.isExhausted("login", 2, MINUTE)).isFalse();
    }

    @Test
    void expired_windows_are_evicted() {
        limiter.tryAcquire("k", 1, MINUTE);
        clock.advance(Duration.ofMinutes(2));
        limiter.evictExpired();
        assertThat(limiter.secondsUntilReset("k")).isZero();
    }

    @Test
    void a_disabled_limiter_allows_everything() {
        RateLimiter disabled = new RateLimiter(clock, properties(false));
        for (int i = 0; i < 100; i++) {
            assertThat(disabled.tryAcquire("k", 1, MINUTE)).isTrue();
        }
    }

    private static AppProperties properties(boolean enabled) {
        return new AppProperties(null, null, "http://localhost", null, new AppProperties.RateLimit(enabled), null);
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
