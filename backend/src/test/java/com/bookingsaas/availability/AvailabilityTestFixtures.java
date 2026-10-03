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
import java.util.LinkedHashMap;
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
    /** A Thursday. Opening hours below are attached to whatever weekday this is. */
    static final LocalDate DATE = LocalDate.of(2026, 8, 20);

    static final long ROOM = 10L;
    static final long EMPLOYEE = 20L;
    static final long CHAIR = 30L;
    static final long TABLE = 40L;

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
        private final Map<Long, ResourceSpec> resources = new LinkedHashMap<>();
        private final Map<Long, List<DayInterval>> resourceHours = new HashMap<>();
        private final Map<Long, List<TimeRange>> busy = new HashMap<>();
        private final Map<Long, List<SeatUse>> seatUse = new HashMap<>();
        private final List<Object[]> requirements = new ArrayList<>();

        private Duration duration = Duration.ofMinutes(60);
        private Duration bufferBefore = Duration.ZERO;
        private Duration bufferAfter = Duration.ZERO;
        private int participants = 1;
        private Duration slotInterval = Duration.ofMinutes(30);
        private boolean enforceLeadTimes;
        private Duration minimumNotice = Duration.ZERO;
        private int maximumAdvanceDays = 365;
        private Instant now = at(DATE.minusDays(1), "12:00");

        Builder resource(long id, long typeId, Integer capacity) {
            resources.put(id, new ResourceSpec(id, typeId, typeName(typeId) + " " + id, capacity));
            return this;
        }

        /** A table other groups can join until its seats are taken. */
        Builder sharedTable(long id, int capacity) {
            resources.put(id, new ResourceSpec(id, TABLE, "Table " + id, capacity, true, null));
            return this;
        }

        /** A private table only given to groups of at least {@code minPartySize}. */
        Builder bigGroupTable(long id, int capacity, int minPartySize) {
            resources.put(id, new ResourceSpec(id, TABLE, "Table " + id, capacity, false, minPartySize));
            return this;
        }

        /** Seats already held on a shared resource. */
        Builder seats(long resourceId, String from, String to, int seats) {
            seatUse.computeIfAbsent(resourceId, key -> new ArrayList<>()).add(new SeatUse(range(from, to), seats));
            return this;
        }

        Builder room(long id, Integer capacity) {
            return resource(id, ROOM, capacity);
        }

        Builder employee(long id) {
            return resource(id, EMPLOYEE, 1);
        }

        /** A requirement whose candidates are every resource of the type added so far. */
        Builder requires(long requirementId, long typeId, int quantity) {
            return requires(requirementId, typeId, quantity, true, false);
        }

        Builder requires(long requirementId, long typeId, int quantity, boolean required, boolean choosable) {
            requirements.add(new Object[]{requirementId, typeId, quantity, required, choosable});
            return this;
        }

        Builder busy(long resourceId, String from, String to) {
            busy.computeIfAbsent(resourceId, key -> new ArrayList<>()).add(range(from, to));
            return this;
        }

        Builder resourceHours(long resourceId, String from, String to) {
            resourceHours.computeIfAbsent(resourceId, key -> new ArrayList<>())
                    .add(new DayInterval(DATE.getDayOfWeek().getValue(), LocalTime.parse(from), LocalTime.parse(to)));
            return this;
        }

        Builder closedAllDay() {
            businessHours.clear();
            return this;
        }

        Builder closure(String from, String to) {
            businessBlocks.add(range(from, to));
            return this;
        }

        Builder duration(int minutes) {
            this.duration = Duration.ofMinutes(minutes);
            return this;
        }

        Builder buffers(int beforeMinutes, int afterMinutes) {
            this.bufferBefore = Duration.ofMinutes(beforeMinutes);
            this.bufferAfter = Duration.ofMinutes(afterMinutes);
            return this;
        }

        Builder participants(int participants) {
            this.participants = participants;
            return this;
        }

        Builder slotInterval(int minutes) {
            this.slotInterval = Duration.ofMinutes(minutes);
            return this;
        }

        Builder customerFacing(Instant now, int noticeMinutes, int advanceDays) {
            this.enforceLeadTimes = true;
            this.now = now;
            this.minimumNotice = Duration.ofMinutes(noticeMinutes);
            this.maximumAdvanceDays = advanceDays;
            return this;
        }

        AvailabilityContext build() {
            List<RequirementSpec> specs = requirements.stream()
                    .map(row -> {
                        long typeId = (long) row[1];
                        return new RequirementSpec((long) row[0], typeId, typeName(typeId), (int) row[2],
                                (boolean) row[3], (boolean) row[4],
                                resources.values().stream()
                                        .filter(resource -> resource.resourceTypeId() == typeId)
                                        .map(ResourceSpec::id)
                                        .toList());
                    })
                    .toList();
            return new AvailabilityContext(ZONE,
                    new ServiceSpec(duration, bufferBefore, bufferAfter, specs),
                    participants,
                    new ReservationRules(slotInterval, minimumNotice, maximumAdvanceDays, enforceLeadTimes),
                    businessHours, businessBlocks, resources, resourceHours, busy, seatUse, now);
        }

        private static String typeName(long typeId) {
            if (typeId == ROOM) {
                return "Room";
            }
            if (typeId == EMPLOYEE) {
                return "Employee";
            }
            if (typeId == CHAIR) {
                return "Chair";
            }
            return typeId == TABLE ? "Table" : "Type" + typeId;
        }
    }
}
