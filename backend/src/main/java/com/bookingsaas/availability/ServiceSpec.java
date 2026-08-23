package com.bookingsaas.availability;

import com.bookingsaas.common.domain.TimeRange;
import com.bookingsaas.service.BookableService;

import java.time.Duration;
import java.time.Instant;

/**
 * The only thing the engine needs to know about a service: how long it runs,
 * how much room it needs around itself, and what it must be paired with.
 *
 * <p>Keeping this separate from the entity lets the engine be unit-tested with
 * no persistence in sight.
 */
public record ServiceSpec(
        Duration duration,
        Duration bufferBefore,
        Duration bufferAfter,
        boolean requiresStaff,
        boolean requiresResource
) {

    public static ServiceSpec of(BookableService service) {
        return new ServiceSpec(service.duration(), service.bufferBefore(), service.bufferAfter(),
                service.isRequiresStaff(), service.isRequiresResource());
    }

    /** The window the customer sees, and the one shown on the calendar. */
    public TimeRange appointmentRange(Instant start) {
        return TimeRange.starting(start, duration);
    }

    /**
     * The window actually reserved, buffers included. Conflict detection uses
     * this everywhere — engine, service layer and database constraint alike.
     */
    public TimeRange blockRange(Instant start) {
        return TimeRange.of(start.minus(bufferBefore), start.plus(duration).plus(bufferAfter));
    }

    /** How far outside a query window a booking can still reach in. */
    public Duration maxSpan() {
        return duration.plus(bufferBefore).plus(bufferAfter);
    }
}
