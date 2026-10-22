package com.bookingsaas.common.ratelimit;

import com.bookingsaas.common.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window counters, kept in memory.
 *
 * <p>Enough for one instance: each instance counts on its own, so with N
 * instances behind a load balancer a client gets up to N times the limit.
 * Moving the counters to Redis (or limiting at the gateway) is the step for
 * running more than one.
 */
@Component
public class RateLimiter {

    private record Window(long startMillis, long lengthMillis, int count) {

        boolean expired(long now) {
            return now - startMillis >= lengthMillis;
        }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final boolean enabled;

    public RateLimiter(Clock clock, AppProperties properties) {
        this.clock = clock;
        this.enabled = properties.rateLimit() == null || properties.rateLimit().enabled();
    }

    /**
     * Counts one use of {@code key} and says whether it is still within
     * {@code limit} uses per {@code period}.
     */
    public boolean tryAcquire(String key, int limit, Duration period) {
        if (!enabled) {
            return true;
        }
        return hit(key, period) <= limit;
    }

    /** Whether {@code key} has already been used {@code limit} times this period, without counting a use. */
    public boolean isExhausted(String key, int limit, Duration period) {
        if (!enabled) {
            return false;
        }
        long now = clock.millis();
        Window window = windows.get(key);
        return window != null && !window.expired(now) && window.count() >= limit;
    }

    /** Counts one use of {@code key}, e.g. a failed login, and returns the count in this window. */
    public int hit(String key, Duration period) {
        long now = clock.millis();
        return windows.compute(key, (k, current) -> current == null || current.expired(now)
                ? new Window(now, period.toMillis(), 1)
                : new Window(current.startMillis(), current.lengthMillis(), current.count() + 1)).count();
    }

    /** Forgets {@code key}, e.g. after a successful login. */
    public void reset(String key) {
        windows.remove(key);
    }

    /** Seconds until the window of {@code key} ends, for a Retry-After header. */
    public long secondsUntilReset(String key) {
        Window window = windows.get(key);
        if (window == null) {
            return 0;
        }
        long left = window.startMillis() + window.lengthMillis() - clock.millis();
        return Math.max(1, (left + 999) / 1000);
    }

    /** Drops finished windows, so the map does not grow with every address ever seen. */
    @Scheduled(fixedDelay = 60_000)
    void evictExpired() {
        long now = clock.millis();
        windows.values().removeIf(window -> window.expired(now));
    }
}
