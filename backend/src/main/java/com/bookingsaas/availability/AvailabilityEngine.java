package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.AvailableSlot;
import com.bookingsaas.availability.dto.DayAvailability;
import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.domain.TimeRange;
import com.bookingsaas.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The availability engine.
 *
 * <p>Pure computation over an {@link AvailabilityContext}: no repositories, no
 * clock of its own, no Spring beyond the stereotype. Every rule in the product
 * spec is one step here, in this order:
 *
 * <ol>
 *   <li>business working hours for the date, in the business timezone</li>
 *   <li>minus business-wide blocked time</li>
 *   <li>candidate starts on the configured slot grid</li>
 *   <li>the appointment must fit entirely inside an open window</li>
 *   <li>lead-time rules (minimum notice, maximum advance)</li>
 *   <li>at least one eligible staff member free, if the service needs one</li>
 *   <li>at least one eligible resource free, if the service needs one</li>
 * </ol>
 *
 * <p>Two windows are involved throughout and must not be confused: the
 * <em>appointment</em> is what the customer books and must sit inside working
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
                        evaluation.staffIds(),
                        evaluation.resourceIds()));
            }
        }
        return new DayAvailability(date, slots);
    }

    /**
     * Candidate start times, stepped by the slot interval from the start of
     * each working interval.
     *
     * <p>The grid is generated from the raw working hours rather than from the
     * windows left after blocked time, so a 12:00-12:40 meeting does not push
     * the afternoon onto a 12:40, 12:55, … grid. Candidates that the blocked
     * time actually invalidates are dropped a moment later by
     * {@link #evaluate}.
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
     * The authoritative check for one exact start time. Booking creation and
     * rescheduling both go through here, so a hand-typed 10:07 is validated by
     * the same rules as a slot picked off the grid.
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
            return SlotEvaluation.rejected(ErrorCode.BOOKING_OUTSIDE_WORKING_HOURS,
                    "The business is not open for the whole of that appointment.");
        }

        List<Long> freeStaff = new ArrayList<>();
        for (Long staffId : ctx.staffIds()) {
            List<TimeRange> windows = day.staffWindows(staffId);
            if (appointment.containedInAny(windows) && !block.overlapsAny(ctx.busyFor(ctx.staffBusy(), staffId))) {
                freeStaff.add(staffId);
            }
        }
        if (service.requiresStaff() && freeStaff.isEmpty()) {
            return SlotEvaluation.rejected(ErrorCode.NO_STAFF_AVAILABLE,
                    "No staff member is available at that time.");
        }

        List<Long> freeResources = new ArrayList<>();
        for (Long resourceId : ctx.resourceIds()) {
            // Resources have no schedule of their own: they are available
            // whenever the business is open and nothing else holds them.
            if (!block.overlapsAny(ctx.busyFor(ctx.resourceBusy(), resourceId))) {
                freeResources.add(resourceId);
            }
        }
        if (service.requiresResource() && freeResources.isEmpty()) {
            return SlotEvaluation.rejected(ErrorCode.NO_RESOURCE_AVAILABLE,
                    "No resource is available at that time.");
        }

        return SlotEvaluation.available(
                service.requiresStaff() || !freeStaff.isEmpty() ? List.copyOf(freeStaff) : List.of(),
                service.requiresResource() || !freeResources.isEmpty() ? List.copyOf(freeResources) : List.of());
    }

    /** Minimum notice and maximum advance; skipped for bookings the business takes itself. */
    private SlotEvaluation checkLeadTimes(AvailabilityContext ctx, Instant start) {
        if (!ctx.rules().enforceLeadTimes()) {
            return null;
        }
        if (start.isBefore(ctx.now())) {
            return SlotEvaluation.rejected(ErrorCode.BOOKING_IN_THE_PAST, "That time has already passed.");
        }
        if (start.isBefore(ctx.now().plus(ctx.rules().minimumNotice()))) {
            return SlotEvaluation.rejected(ErrorCode.BOOKING_TOO_SOON,
                    "Bookings need at least " + describe(ctx.rules().minimumNotice()) + " notice.");
        }
        LocalDate lastBookableDay = LocalDate.ofInstant(ctx.now(), ctx.zone())
                .plusDays(ctx.rules().maximumAdvanceDays());
        if (LocalDate.ofInstant(start, ctx.zone()).isAfter(lastBookableDay)) {
            return SlotEvaluation.rejected(ErrorCode.BOOKING_TOO_FAR_IN_ADVANCE,
                    "Bookings can be made up to " + ctx.rules().maximumAdvanceDays() + " days ahead.");
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

        java.util.Map<Long, List<TimeRange>> staffWindows = new java.util.HashMap<>();
        for (Long staffId : ctx.staffIds()) {
            List<DayInterval> personal = ctx.staffHours().get(staffId);
            if (personal == null || personal.isEmpty()) {
                // No personal schedule: the staff member follows the business.
                staffWindows.put(staffId, openWindows);
            } else {
                List<TimeRange> own = TimeRange.merge(toRanges(ctx, date, personal));
                staffWindows.put(staffId, own.isEmpty() ? List.of() : TimeRange.intersect(own, openWindows));
            }
        }
        return new DaySchedule(gridWindows, openWindows, staffWindows);
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
     * @param gridWindows working hours as configured, used to lay out the slot grid
     * @param openWindows the same windows minus business-wide blocked time
     */
    private record DaySchedule(List<TimeRange> gridWindows,
                               List<TimeRange> openWindows,
                               java.util.Map<Long, List<TimeRange>> staffWindowsById) {

        List<TimeRange> staffWindows(Long staffId) {
            return staffWindowsById.getOrDefault(staffId, List.of());
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
