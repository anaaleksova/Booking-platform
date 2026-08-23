package com.bookingsaas.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * "Now" is injected, never taken from {@code Instant.now()} inside domain code,
 * so lead-time and reminder rules can be tested at a fixed point in time.
 *
 * <p>UTC on purpose: business-local time is derived from the tenant's timezone,
 * never from the server's.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
