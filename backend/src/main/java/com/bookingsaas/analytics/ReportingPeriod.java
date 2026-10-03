package com.bookingsaas.analytics;

import com.bookingsaas.common.error.ApiException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * An inclusive range of business-local calendar days, and the half-open
 * instant window {@code [start of from, start of to+1)} it covers.
 *
 * <p>Always day-aligned, never "now": a reservation later today belongs to
 * today's figures even when the report is pulled in the morning.
 */
record ReportingPeriod(LocalDate from, LocalDate to, ZoneId zone) {

    static final int MAX_DAYS = 366 * 2;

    ReportingPeriod {
        if (to.isBefore(from)) {
            throw ApiException.validation("'to' must not be before 'from'.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw ApiException.validation("Reports can cover at most two years.");
        }
    }

    static ReportingPeriod of(LocalDate from, LocalDate to, LocalDate defaultFrom, LocalDate defaultTo, ZoneId zone) {
        return new ReportingPeriod(from != null ? from : defaultFrom, to != null ? to : defaultTo, zone);
    }

    Instant start() {
        return from.atStartOfDay(zone).toInstant();
    }

    Instant end() {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }

    long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }
}
