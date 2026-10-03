package com.bookingsaas.availability;

import com.bookingsaas.common.domain.TimeRange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * What the engine needs to know about a service: how long it runs, how much
 * room it needs around itself, and which resources it requires.
 *
 * <p>Kept separate from the entity so the engine can be unit-tested with no
 * persistence in sight.
 */
public record ServiceSpec(
        Duration duration,
        Duration bufferBefore,
        Duration bufferAfter,
        List<RequirementSpec> requirements
) {

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

    /** How far outside a query window a reservation can still reach in. */
    public Duration maxSpan() {
        return duration.plus(bufferBefore).plus(bufferAfter);
    }
}
