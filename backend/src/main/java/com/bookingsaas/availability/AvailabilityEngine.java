package com.bookingsaas.availability;

import com.bookingsaas.availability.AllocationFinder.Allocation;
import com.bookingsaas.availability.ResourceAllocation.AllocatedResource;
import com.bookingsaas.availability.SlotEvaluation.RequirementOption;
import com.bookingsaas.availability.dto.AvailableSlot;
import com.bookingsaas.availability.dto.DayAvailability;
import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.domain.TimeRange;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The availability engine.
 *
 * <p>Pure computation over an {@link AvailabilityContext}: no repositories, no
 * clock of its own, no Spring beyond the stereotype. For a start time, in this
 * order:
 *
 * <ol>
 *   <li>lead-time rules (minimum notice, maximum advance) — customer-facing only</li>
 *   <li>the business must be open for the whole appointment (opening hours
 *       minus business-wide closures, in the business timezone)</li>
 *   <li>for every resource requirement of the service: enough eligible
 *       resources of the type must be free — inside their own schedule, with
 *       no reservation or blocked time touching the block window — and
 *       {@code quantity} of them must together have the capacity for the
 *       group</li>
 * </ol>
 *
 * <p>A <em>combinable</em> requirement treats {@code quantity} as a minimum:
 * when no single resource seats the group, the {@link AllocationFinder} puts
 * free ones together (Table 3 + Table 4 for eight). Those combinations are
 * worked out per request and never stored as such — the reservation simply
 * holds every resource it was given.
 *
 * <p>A <em>shared</em> resource is free while it has seats left: other
 * bookings use up seats rather than the whole resource, and only the seats
 * still free count towards the group. A private resource with a minimum group
 * size is not offered to smaller groups.
 *
 * <p>Two windows are involved throughout and must not be confused: the
 * <em>appointment</em> is what the customer books and must sit inside opening
 * hours; the <em>block</em> is the appointment plus buffers and is what must
 * not collide with anything else.
 */
@Component
public class AvailabilityEngine {

    /** A cap on how much can be asked for in one request. */
    public static final int MAX_DAYS_PER_QUERY = 62;

    // --- slot listing ------------------------------------------------------

    public List<DayAvailability> availableSlots(AvailabilityContext ctx, LocalDate from, LocalDate to) {
        List<DayAvailability> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            result.add(slotsForDay(ctx, date));
        }
        return result;
    }

    public DayAvailability slotsForDay(AvailabilityContext ctx, LocalDate date) {
        DaySchedule day = scheduleFor(ctx, date);
        if (day.gridWindows().isEmpty()) {
            return DayAvailability.empty(date);       // closed that weekday
        }

        List<AvailableSlot> slots = new ArrayList<>();
        for (Instant start : candidateStarts(ctx, day)) {
            SlotEvaluation evaluation = evaluate(ctx, day, start);
            if (evaluation.available()) {
                slots.add(new AvailableSlot(
                        LocalTime.from(start.atZone(ctx.zone())),
                        start,
                        start.plus(ctx.service().duration()),
                        evaluation.options()));
            }
        }
        return new DayAvailability(date, slots);
    }

    /**
     * Candidate start times, stepped by the slot interval from the start of
     * each opening interval.
     *
     * <p>The grid is generated from the raw opening hours rather than the
     * windows left after closures, so a 12:00-12:40 closure does not push the
     * afternoon onto a 12:40, 13:10, … grid. Candidates that the closure
     * actually invalidates are dropped a moment later by {@link #evaluate}.
     */
    private List<Instant> candidateStarts(AvailabilityContext ctx, DaySchedule day) {
        Duration step = ctx.rules().slotInterval();
        Duration duration = ctx.service().duration();
        List<Instant> starts = new ArrayList<>();

        for (TimeRange window : day.gridWindows()) {
            Instant cursor = window.start();
            // The last candidate is the latest start whose appointment still
            // finishes before the business closes.
            while (!cursor.plus(duration).isAfter(window.end())) {
                starts.add(cursor);
                cursor = cursor.plus(step);
            }
        }
        return starts;
    }

    // --- single start time -------------------------------------------------

    /**
     * The authoritative check for one exact start time. Reservation creation,
     * rescheduling and resource reassignment all go through here, so a
     * hand-typed 10:07 is validated by the same rules as a slot off the grid.
     */
    public SlotEvaluation evaluate(AvailabilityContext ctx, Instant start) {
        return evaluate(ctx, scheduleFor(ctx, LocalDate.ofInstant(start, ctx.zone())), start);
    }

    private SlotEvaluation evaluate(AvailabilityContext ctx, DaySchedule day, Instant start) {
        ServiceSpec service = ctx.service();
        TimeRange appointment = service.appointmentRange(start);
        TimeRange block = service.blockRange(start);

        SlotEvaluation leadTime = checkLeadTimes(ctx, start);
        if (leadTime != null) {
            return leadTime;
        }

        if (!appointment.containedInAny(day.openWindows())) {
            return SlotEvaluation.rejected(ErrorCode.OUTSIDE_BUSINESS_HOURS,
                    "The business is not open for the whole of that reservation.");
        }

        List<RequirementOption> options = new ArrayList<>();
        for (RequirementSpec requirement : service.requirements()) {
            List<ResourceSpec> free = new ArrayList<>();
            Map<Long, Integer> seatsLeft = new HashMap<>();
            for (Long resourceId : requirement.eligibleResourceIds()) {
                ResourceSpec resource = ctx.resources().get(resourceId);
                if (resource == null
                        || !appointment.containedInAny(day.resourceWindows(ctx, resourceId))
                        || block.overlapsAny(ctx.busyOf(resourceId))) {
                    continue;
                }
                if (resource.shared() && resource.capacity() != null) {
                    int left = resource.capacity() - peakSeats(ctx.seatsOf(resourceId), block);
                    if (left > 0) {
                        seatsLeft.put(resourceId, left);
                        free.add(resource.withCapacity(left));
                    }
                } else if (!overlapsSeats(ctx.seatsOf(resourceId), block)
                        && !keptForBiggerGroups(resource, ctx.participants())) {
                    free.add(resource);
                }
            }
            List<ResourceSpec> ordered = byPreference(ctx, free);
            List<Allocation> allocations = requirement.combinable()
                    ? AllocationFinder.find(ordered, requirement.quantity(), ctx.participants(),
                            AllocationFinder.DEFAULT_LIMIT)
                    : List.of();
            boolean satisfiable = requirement.combinable()
                    ? !allocations.isEmpty()
                    : bestFit(ordered, requirement.quantity(), ctx.participants()) != null;

            if (requirement.required() && !satisfiable) {
                return SlotEvaluation.rejected(ErrorCode.RESOURCE_NOT_AVAILABLE,
                        unavailableMessage(requirement, free.size(), ctx.participants()));
            }
            options.add(new RequirementOption(requirement, ordered.stream().map(ResourceSpec::id).toList(),
                    satisfiable, Map.copyOf(seatsLeft), allocations));
        }
        return SlotEvaluation.available(List.copyOf(options));
    }

    // --- allocation --------------------------------------------------------

    /**
     * Validates a start time and fixes the concrete resources.
     *
     * @param chosen requirement id → resources picked by the customer or the
     *               admin. Requirements not in the map are assigned
     *               automatically, best fit first.
     * @throws ApiException when the slot is not available or a choice is not
     *                      valid for it
     */
    public ResourceAllocation allocate(AvailabilityContext ctx, Instant start, Map<Long, List<Long>> chosen) {
        SlotEvaluation evaluation = evaluate(ctx, start);
        if (!evaluation.available()) {
            throw evaluation.toException();
        }

        Set<Long> knownRequirements = new HashSet<>();
        List<AllocatedResource> allocated = new ArrayList<>();
        for (RequirementOption option : evaluation.options()) {
            RequirementSpec requirement = option.requirement();
            knownRequirements.add(requirement.requirementId());
            List<Long> picked = chosen.get(requirement.requirementId());

            List<ResourceSpec> resources = picked == null || picked.isEmpty()
                    ? autoAssign(ctx, option)
                    : validateChoice(ctx, option, picked);
            allocated.addAll(seat(requirement, resources, ctx.participants()));
        }

        for (Long requirementId : chosen.keySet()) {
            if (!knownRequirements.contains(requirementId)) {
                throw ApiException.of(ErrorCode.INVALID_RESOURCE_SELECTION,
                        "The selection refers to a requirement this service does not have.");
            }
        }

        TimeRange appointment = ctx.service().appointmentRange(start);
        TimeRange block = ctx.service().blockRange(start);
        return new ResourceAllocation(appointment.start(), appointment.end(), block.start(), block.end(),
                List.copyOf(allocated));
    }

    private List<ResourceSpec> autoAssign(AvailabilityContext ctx, RequirementOption option) {
        if (option.requirement().combinable()) {
            // The best-ranked combination; none only for an unsatisfied optional requirement.
            return option.allocations().isEmpty() ? List.of() : option.allocations().get(0).resources();
        }
        List<ResourceSpec> ordered = option.candidateIds().stream().map(id -> asSeen(ctx, option, id)).toList();
        List<ResourceSpec> fit = bestFit(ordered, option.requirement().quantity(), ctx.participants());
        // Only an optional requirement reaches here unsatisfied: skip it.
        return fit == null ? List.of() : fit;
    }

    /**
     * Seats the group: a shared resource holds only the seats the group still
     * needs, the rest stay bookable; a private one is taken whole.
     */
    private static List<AllocatedResource> seat(RequirementSpec requirement, List<ResourceSpec> resources,
                                                int participants) {
        List<AllocatedResource> allocated = new ArrayList<>();
        long unseated = participants;
        for (ResourceSpec resource : resources) {
            long used = Math.min(resource.effectiveCapacity(), Math.max(unseated, 0));
            Integer seats = resource.shared() ? (int) Math.max(1, used) : null;
            allocated.add(new AllocatedResource(requirement.requirementId(), resource.id(), seats));
            unseated -= used;
        }
        return allocated;
    }

    /** The resource as it is at this time: a shared one with only its free seats. */
    private static ResourceSpec asSeen(AvailabilityContext ctx, RequirementOption option, Long id) {
        Integer left = option.seatsLeft().get(id);
        ResourceSpec resource = ctx.resources().get(id);
        return left == null ? resource : resource.withCapacity(left);
    }

    private static boolean keptForBiggerGroups(ResourceSpec resource, int participants) {
        return !resource.shared() && resource.minPartySize() != null && participants < resource.minPartySize();
    }

    /** Whether any seat booking touches the window — a private resource then is not wholly free. */
    private static boolean overlapsSeats(List<SeatUse> uses, TimeRange window) {
        return uses.stream().anyMatch(use -> use.range().overlaps(window));
    }

    /**
     * The most seats in use at any moment of the window. Occupancy only rises
     * when a booking starts, so the window start and every start inside it are
     * the moments to check.
     */
    static int peakSeats(List<SeatUse> uses, TimeRange window) {
        List<SeatUse> overlapping = uses.stream().filter(use -> use.range().overlaps(window)).toList();
        List<Instant> moments = new ArrayList<>();
        moments.add(window.start());
        overlapping.stream().map(use -> use.range().start())
                .filter(start -> start.isAfter(window.start()) && start.isBefore(window.end()))
                .forEach(moments::add);
        int peak = 0;
        for (Instant moment : moments) {
            int load = overlapping.stream()
                    .filter(use -> !use.range().start().isAfter(moment) && use.range().end().isAfter(moment))
                    .mapToInt(SeatUse::seats)
                    .sum();
            peak = Math.max(peak, load);
        }
        return peak;
    }

    private List<ResourceSpec> validateChoice(AvailabilityContext ctx, RequirementOption option, List<Long> picked) {
        RequirementSpec requirement = option.requirement();
        String type = requirement.resourceTypeName();
        Set<Long> distinct = new HashSet<>(picked);
        if (distinct.size() != picked.size()) {
            throw ApiException.of(ErrorCode.INVALID_RESOURCE_SELECTION, "Select each " + type + " only once.");
        }
        if (!requirement.combinable() && picked.size() != requirement.quantity()) {
            throw ApiException.of(ErrorCode.INVALID_RESOURCE_SELECTION,
                    "Select exactly " + requirement.quantity() + " × " + type + ".");
        }
        for (Long id : picked) {
            if (!requirement.eligibleResourceIds().contains(id)) {
                throw ApiException.of(ErrorCode.INVALID_RESOURCE_SELECTION,
                        "The selected " + type + " cannot be used for this service.");
            }
            ResourceSpec resource = ctx.resources().get(id);
            if (resource != null && keptForBiggerGroups(resource, ctx.participants())) {
                throw ApiException.of(ErrorCode.INVALID_RESOURCE_SELECTION,
                        "The selected " + type + " is kept for groups of " + resource.minPartySize() + " or more.");
            }
            if (!option.candidateIds().contains(id)) {
                throw ApiException.of(ErrorCode.RESOURCE_NOT_AVAILABLE,
                        "The selected " + type + " is no longer available at that time.");
            }
        }
        List<ResourceSpec> seen = picked.stream().map(id -> asSeen(ctx, option, id)).toList();
        if (requirement.combinable()) {
            String rejection = AllocationFinder.rejectionOf(seen, requirement.quantity(), ctx.participants());
            if (rejection != null) {
                throw ApiException.of(ErrorCode.INVALID_RESOURCE_SELECTION,
                        "That combination of " + type + " resources does not work: " + rejection);
            }
            return seen;
        }
        long capacity = seen.stream().mapToLong(ResourceSpec::effectiveCapacity).sum();
        if (capacity < ctx.participants()) {
            throw ApiException.of(ErrorCode.INVALID_RESOURCE_SELECTION,
                    "The selected " + type + " cannot accommodate " + ctx.participants() + " participants"
                            + (seen.stream().anyMatch(ResourceSpec::shared) ? " — not enough seats are left." : "."));
        }
        return seen;
    }

    /**
     * Smallest adequate combination: from resources sorted by ascending
     * capacity, the first run of {@code quantity} whose combined capacity
     * covers the group. A party of two gets the two-seat table, not the
     * six-seat one; among equals, the least busy resource is used first.
     *
     * @return the chosen resources, or {@code null} when none suffices
     */
    static List<ResourceSpec> bestFit(List<ResourceSpec> orderedByCapacity, int quantity, int participants) {
        if (orderedByCapacity.size() < quantity) {
            return null;
        }
        for (int i = 0; i + quantity <= orderedByCapacity.size(); i++) {
            List<ResourceSpec> window = orderedByCapacity.subList(i, i + quantity);
            long capacity = window.stream().mapToLong(ResourceSpec::effectiveCapacity).sum();
            if (capacity >= participants) {
                return List.copyOf(window);
            }
        }
        return null;
    }

    /** Ascending capacity (unlimited last), then least busy, then the incoming (alphabetical) order. */
    private static List<ResourceSpec> byPreference(AvailabilityContext ctx, List<ResourceSpec> resources) {
        List<ResourceSpec> sorted = new ArrayList<>(resources);
        sorted.sort(Comparator.comparingLong(ResourceSpec::effectiveCapacity)
                .thenComparingInt(resource -> ctx.busyOf(resource.id()).size()));
        return sorted;
    }

    private static String unavailableMessage(RequirementSpec requirement, int freeCount, int participants) {
        String type = requirement.resourceTypeName();
        if (requirement.combinable() && freeCount >= requirement.quantity()) {
            return "No available " + type + ", alone or combined, can accommodate " + participants
                    + " participants at that time.";
        }
        if (freeCount < requirement.quantity()) {
            return requirement.quantity() == 1
                    ? "No " + type + " is available at that time."
                    : "Not enough " + type + " resources are available at that time ("
                    + requirement.quantity() + " needed).";
        }
        return "No available " + type + " can accommodate " + participants + " participants at that time.";
    }

    /** Minimum notice and maximum advance; skipped for reservations the business takes itself. */
    private SlotEvaluation checkLeadTimes(AvailabilityContext ctx, Instant start) {
        if (!ctx.rules().enforceLeadTimes()) {
            return null;
        }
        if (start.isBefore(ctx.now())) {
            return SlotEvaluation.rejected(ErrorCode.RESERVATION_IN_THE_PAST, "That time has already passed.");
        }
        if (start.isBefore(ctx.now().plus(ctx.rules().minimumNotice()))) {
            return SlotEvaluation.rejected(ErrorCode.RESERVATION_TOO_SOON,
                    "Reservations need at least " + describe(ctx.rules().minimumNotice()) + " notice.");
        }
        LocalDate lastBookableDay = LocalDate.ofInstant(ctx.now(), ctx.zone())
                .plusDays(ctx.rules().maximumAdvanceDays());
        if (LocalDate.ofInstant(start, ctx.zone()).isAfter(lastBookableDay)) {
            return SlotEvaluation.rejected(ErrorCode.RESERVATION_TOO_FAR_IN_ADVANCE,
                    "Reservations can be made up to " + ctx.rules().maximumAdvanceDays() + " days ahead.");
        }
        return null;
    }

    // --- per-day schedule --------------------------------------------------

    /** Resolves a date's windows once, then reuses them for every candidate. */
    private DaySchedule scheduleFor(AvailabilityContext ctx, LocalDate date) {
        List<TimeRange> gridWindows = TimeRange.merge(toRanges(ctx, date, ctx.businessHours()));
        List<TimeRange> openWindows = gridWindows.isEmpty()
                ? List.of()
                : TimeRange.subtractAll(gridWindows, ctx.businessBlocks());
        return new DaySchedule(date, gridWindows, openWindows, new HashMap<>());
    }

    /**
     * Wall-clock intervals to absolute instants on a given date.
     *
     * <p>{@code atZone} resolves DST for us: a 02:30 start on a spring-forward
     * morning moves to the first valid instant instead of throwing, and an
     * autumn day's ambiguous hour resolves to the earlier offset.
     */
    private static List<TimeRange> toRanges(AvailabilityContext ctx, LocalDate date, List<DayInterval> intervals) {
        List<TimeRange> ranges = new ArrayList<>();
        int dayOfWeek = date.getDayOfWeek().getValue();
        for (DayInterval interval : intervals) {
            if (interval.dayOfWeek() != dayOfWeek) {
                continue;
            }
            ZonedDateTime start = date.atTime(interval.startTime()).atZone(ctx.zone());
            ZonedDateTime end = date.atTime(interval.endTime()).atZone(ctx.zone());
            if (start.toInstant().isBefore(end.toInstant())) {
                ranges.add(TimeRange.of(start.toInstant(), end.toInstant()));
            }
        }
        return ranges;
    }

    /**
     * @param gridWindows opening hours as configured, used to lay out the slot grid
     * @param openWindows the same windows minus business-wide closures
     * @param resourceWindowCache each resource's windows for the day, computed on first use
     */
    private record DaySchedule(LocalDate date,
                               List<TimeRange> gridWindows,
                               List<TimeRange> openWindows,
                               Map<Long, List<TimeRange>> resourceWindowCache) {

        /** When the resource can be used that day: its own hours within the open windows. */
        List<TimeRange> resourceWindows(AvailabilityContext ctx, Long resourceId) {
            return resourceWindowCache.computeIfAbsent(resourceId, id -> {
                List<DayInterval> own = ctx.resourceHours().get(id);
                if (own == null || own.isEmpty()) {
                    // No schedule of its own: available whenever the business is.
                    return openWindows;
                }
                List<TimeRange> ranges = TimeRange.merge(toRanges(ctx, date, own));
                return ranges.isEmpty() ? List.of() : TimeRange.intersect(ranges, openWindows);
            });
        }
    }

    private static String describe(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0 && minutes > 0) {
            return hours + "h " + minutes + "m";
        }
        return hours > 0 ? hours + " hour" + (hours == 1 ? "" : "s") : minutes + " minutes";
    }
}
