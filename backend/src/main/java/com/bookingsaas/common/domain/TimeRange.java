package com.bookingsaas.common.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A half-open interval {@code [start, end)} on the absolute timeline.
 *
 * <p>Half-open is what makes back-to-back appointments legal: a booking ending
 * at 11:00 and one starting at 11:00 do not overlap. The overlap rule lives
 * here alone so availability, conflict checks and the database predicate can
 * never drift apart.
 */
public record TimeRange(Instant start, Instant end) implements Comparable<TimeRange> {

    public TimeRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("TimeRange bounds must not be null");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("TimeRange start must be before end: " + start + " .. " + end);
        }
    }

    public static TimeRange of(Instant start, Instant end) {
        return new TimeRange(start, end);
    }

    public static TimeRange starting(Instant start, Duration duration) {
        return new TimeRange(start, start.plus(duration));
    }

    /** {@code newStart < existingEnd AND newEnd > existingStart}. */
    public boolean overlaps(TimeRange other) {
        return start.isBefore(other.end) && end.isAfter(other.start);
    }

    /** True when this range fits entirely inside {@code other}. */
    public boolean containedIn(TimeRange other) {
        return !start.isBefore(other.start) && !end.isAfter(other.end);
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    /**
     * Removes {@code cut} from this range, returning zero, one or two remaining
     * pieces. This is the primitive the availability engine uses to punch
     * bookings, breaks and blocked periods out of a working window.
     */
    public List<TimeRange> subtract(TimeRange cut) {
        if (!overlaps(cut)) {
            return List.of(this);
        }
        List<TimeRange> remainder = new ArrayList<>(2);
        if (start.isBefore(cut.start)) {
            remainder.add(new TimeRange(start, cut.start));
        }
        if (end.isAfter(cut.end)) {
            remainder.add(new TimeRange(cut.end, end));
        }
        return remainder;
    }

    /** Subtracts every cut from every range, preserving chronological order. */
    public static List<TimeRange> subtractAll(List<TimeRange> ranges, List<TimeRange> cuts) {
        List<TimeRange> current = new ArrayList<>(ranges);
        for (TimeRange cut : cuts) {
            List<TimeRange> next = new ArrayList<>(current.size() + 1);
            for (TimeRange range : current) {
                next.addAll(range.subtract(cut));
            }
            current = next;
        }
        current.sort(Comparator.naturalOrder());
        return current;
    }

    /** The overlap with {@code other}, or empty when they merely touch. */
    public Optional<TimeRange> intersection(TimeRange other) {
        Instant maxStart = start.isAfter(other.start) ? start : other.start;
        Instant minEnd = end.isBefore(other.end) ? end : other.end;
        return maxStart.isBefore(minEnd) ? Optional.of(new TimeRange(maxStart, minEnd)) : Optional.empty();
    }

    /**
     * Every piece present in both sets. This is how a resource's own hours
     * are narrowed to the hours the business is actually open.
     */
    public static List<TimeRange> intersect(List<TimeRange> left, List<TimeRange> right) {
        List<TimeRange> result = new ArrayList<>();
        for (TimeRange a : left) {
            for (TimeRange b : right) {
                a.intersection(b).ifPresent(result::add);
            }
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    /** True when this range fits inside at least one of {@code windows}. */
    public boolean containedInAny(List<TimeRange> windows) {
        for (TimeRange window : windows) {
            if (containedIn(window)) {
                return true;
            }
        }
        return false;
    }

    /** True when this range collides with any of {@code others}. */
    public boolean overlapsAny(List<TimeRange> others) {
        for (TimeRange other : others) {
            if (overlaps(other)) {
                return true;
            }
        }
        return false;
    }

    /** Merges overlapping or touching ranges into the smallest equivalent set. */
    public static List<TimeRange> merge(List<TimeRange> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }
        List<TimeRange> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.naturalOrder());

        List<TimeRange> merged = new ArrayList<>(sorted.size());
        Instant currentStart = sorted.getFirst().start();
        Instant currentEnd = sorted.getFirst().end();

        for (TimeRange range : sorted.subList(1, sorted.size())) {
            if (range.start().isAfter(currentEnd)) {
                merged.add(new TimeRange(currentStart, currentEnd));
                currentStart = range.start();
                currentEnd = range.end();
            } else if (range.end().isAfter(currentEnd)) {
                currentEnd = range.end();
            }
        }
        merged.add(new TimeRange(currentStart, currentEnd));
        return merged;
    }

    @Override
    public int compareTo(TimeRange other) {
        int byStart = start.compareTo(other.start);
        return byStart != 0 ? byStart : end.compareTo(other.end);
    }
}
