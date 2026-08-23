package com.bookingsaas.availability;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.domain.TimeRange;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small builder for engine tests.
 *
 * <p>The engine takes a fully loaded context, which is exactly what makes it
 * testable: each test states one scenario in a few lines, with no database,
 * no Spring context and no clock.
 */
final class AvailabilityTestFixtures {

    static final ZoneId ZONE = ZoneId.of("Europe/Skopje");
    /** A Thursday. Working hours below are attached to whatever weekday this is. */
    static final LocalDate DATE = LocalDate.of(2026, 8, 20);

    private AvailabilityTestFixtures() {
    }

    static Instant at(String time) {
        return at(DATE, time);
    }

    static Instant at(LocalDate date, String time) {
        return date.atTime(LocalTime.parse(time)).atZone(ZONE).toInstant();
    }

    static TimeRange range(String from, String to) {
        return TimeRange.of(at(from), at(to));
    }

    static Builder context() {
        return new Builder();
    }

    static final class Builder {

        private final List<DayInterval> businessHours = new ArrayList<>(
                List.of(new DayInterval(DATE.getDayOfWeek().getValue(), LocalTime.of(9, 0), LocalTime.of(17, 0))));
        private final List<TimeRange> businessBlocks = new ArrayList<>();
        private final List<Long> staffIds = new ArrayList<>(List.of(1L));
        private final Map<Long, List<DayInterval>> staffHours = new HashMap<>();
        private final Map<Long, List<TimeRange>> staffBusy = new HashMap<>();
        private final List<Long> resourceIds = new ArrayList<>();
        private final Map<Long, List<TimeRange>> resourceBusy = new HashMap<>();

        private ServiceSpec service = new ServiceSpec(Duration.ofMinutes(60), Duration.ZERO, Duration.ZERO,
                true, false);
        private BookingRules rules = new BookingRules(Duration.ofMinutes(60), Duration.ofHours(2), 60, false);
        /** Well before the test date, so lead-time rules never fire unless asked for. */
        private Instant now = at(DATE.minusDays(3), "08:00");

        Builder businessHours(String from, String to) {
            businessHours.clear();
            return addBusinessHours(from, to);
        }

        Builder addBusinessHours(String from, String to) {
            businessHours.add(new DayInterval(DATE.getDayOfWeek().getValue(),
                    LocalTime.parse(from), LocalTime.parse(to)));
            return this;
        }

        Builder closed() {
            businessHours.clear();
            return this;
        }

        Builder businessBlock(String from, String to) {
            businessBlocks.add(range(from, to));
            return this;
        }

        Builder service(int durationMinutes) {
            service = new ServiceSpec(Duration.ofMinutes(durationMinutes), service.bufferBefore(),
                    service.bufferAfter(), service.requiresStaff(), service.requiresResource());
            return this;
        }

        Builder buffers(int before, int after) {
            service = new ServiceSpec(service.duration(), Duration.ofMinutes(before), Duration.ofMinutes(after),
                    service.requiresStaff(), service.requiresResource());
            return this;
        }

        Builder requires(boolean staff, boolean resource) {
            service = new ServiceSpec(service.duration(), service.bufferBefore(), service.bufferAfter(),
                    staff, resource);
            return this;
        }

        Builder slotInterval(int minutes) {
            rules = new BookingRules(Duration.ofMinutes(minutes), rules.minimumNotice(),
                    rules.maximumAdvanceDays(), rules.enforceLeadTimes());
            return this;
        }

        Builder leadTimes(int minimumNoticeMinutes, int maximumAdvanceDays) {
            rules = new BookingRules(rules.slotInterval(), Duration.ofMinutes(minimumNoticeMinutes),
                    maximumAdvanceDays, true);
            return this;
        }

        Builder now(Instant instant) {
            this.now = instant;
            return this;
        }

        Builder staff(Long... ids) {
            staffIds.clear();
            staffIds.addAll(List.of(ids));
            return this;
        }

        Builder staffHours(Long staffId, String from, String to) {
            staffHours.computeIfAbsent(staffId, key -> new ArrayList<>())
                    .add(new DayInterval(DATE.getDayOfWeek().getValue(), LocalTime.parse(from), LocalTime.parse(to)));
            return this;
        }

        /** Marks a staff member as scheduled, but not on the test's weekday. */
        Builder staffOffToday(Long staffId) {
            staffHours.computeIfAbsent(staffId, key -> new ArrayList<>())
                    .add(new DayInterval(DATE.plusDays(1).getDayOfWeek().getValue(),
                            LocalTime.of(9, 0), LocalTime.of(17, 0)));
            return this;
        }

        Builder staffBusy(Long staffId, String from, String to) {
            staffBusy.computeIfAbsent(staffId, key -> new ArrayList<>()).add(range(from, to));
            return this;
        }

        Builder resources(Long... ids) {
            resourceIds.clear();
            resourceIds.addAll(List.of(ids));
            return this;
        }

        Builder resourceBusy(Long resourceId, String from, String to) {
            resourceBusy.computeIfAbsent(resourceId, key -> new ArrayList<>()).add(range(from, to));
            return this;
        }

        AvailabilityContext build() {
            return new AvailabilityContext(ZONE, service, rules, List.copyOf(businessHours),
                    List.copyOf(businessBlocks), List.copyOf(staffIds), Map.copyOf(staffHours),
                    Map.copyOf(staffBusy), List.copyOf(resourceIds), Map.copyOf(resourceBusy), now);
        }
    }
}
