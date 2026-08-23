package com.bookingsaas.common.domain;

import com.bookingsaas.common.error.ApiException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One recurring wall-clock interval on a weekday, e.g. {@code Mon 09:00-13:00}.
 *
 * <p>Wall-clock rather than instants on purpose: "Monday 09:00" must stay 09:00
 * for the business when the clocks change. Several intervals on one day is how
 * a lunch break is expressed — 09:00-13:00 plus 14:00-18:00 — so no separate
 * "break" concept is needed.
 */
public record DayInterval(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime
) {

    public DayOfWeek day() {
        return DayOfWeek.of(dayOfWeek);
    }

    /**
     * Rejects reversed and overlapping intervals before anything is persisted;
     * overlapping working hours would otherwise silently produce duplicate
     * candidate slots.
     */
    public static void validate(List<DayInterval> intervals) {
        if (intervals == null || intervals.isEmpty()) {
            return;
        }
        for (DayInterval interval : intervals) {
            // Checked here rather than relying on bean validation: a JSON array
            // request body does not cascade constraints to its elements.
            if (interval == null || interval.dayOfWeek() == null
                    || interval.startTime() == null || interval.endTime() == null) {
                throw ApiException.validation("Each working-hours entry needs a day, a start time and an end time.");
            }
            if (interval.dayOfWeek() < 1 || interval.dayOfWeek() > 7) {
                throw ApiException.validation("Day of week must be 1 (Monday) to 7 (Sunday).");
            }
            if (!interval.startTime().isBefore(interval.endTime())) {
                throw ApiException.validation("Working hours must start before they end: "
                        + interval.day() + " " + interval.startTime() + "-" + interval.endTime());
            }
        }
        List<DayInterval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(DayInterval::dayOfWeek).thenComparing(DayInterval::startTime));
        for (int i = 1; i < sorted.size(); i++) {
            DayInterval previous = sorted.get(i - 1);
            DayInterval current = sorted.get(i);
            if (previous.dayOfWeek().equals(current.dayOfWeek())
                    && current.startTime().isBefore(previous.endTime())) {
                throw ApiException.validation("Working hours on " + current.day() + " overlap: "
                        + previous.startTime() + "-" + previous.endTime() + " and "
                        + current.startTime() + "-" + current.endTime());
            }
        }
    }
}
