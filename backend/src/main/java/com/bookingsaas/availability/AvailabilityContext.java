package com.bookingsaas.availability;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.domain.TimeRange;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Everything the engine needs for one availability question, already loaded.
 *
 * <p>The engine performs no I/O: give it this record and it is fully
 * deterministic, which is what makes the availability rules testable one at a
 * time.
 *
 * @param participants   group size the reservation is for
 * @param resources      every candidate resource referenced by the service's
 *                       requirements, by id
 * @param resourceHours  per resource, its own weekly schedule. A resource absent
 *                       from this map follows the business hours
 * @param resourceBusy   per resource, the windows it is wholly taken —
 *                       whole-resource reservations (buffers included) and
 *                       blocked time merged, because the engine treats them
 *                       identically
 * @param seatUse        per resource, seats held by bookings that share it
 * @param specialHours   dates whose hours replace the weekly {@code businessHours};
 *                       an empty list closes the date
 */
public record AvailabilityContext(
        ZoneId zone,
        ServiceSpec service,
        int participants,
        ReservationRules rules,
        List<DayInterval> businessHours,
        List<TimeRange> businessBlocks,
        Map<Long, ResourceSpec> resources,
        Map<Long, List<DayInterval>> resourceHours,
        Map<Long, List<TimeRange>> resourceBusy,
        Map<Long, List<SeatUse>> seatUse,
        Instant now,
        Map<LocalDate, List<DayInterval>> specialHours
) {

    /** A context without special dates. */
    public AvailabilityContext(ZoneId zone, ServiceSpec service, int participants, ReservationRules rules,
                               List<DayInterval> businessHours, List<TimeRange> businessBlocks,
                               Map<Long, ResourceSpec> resources, Map<Long, List<DayInterval>> resourceHours,
                               Map<Long, List<TimeRange>> resourceBusy, Map<Long, List<SeatUse>> seatUse,
                               Instant now) {
        this(zone, service, participants, rules, businessHours, businessBlocks, resources, resourceHours,
                resourceBusy, seatUse, now, Map.of());
    }

    /** A context without any shared-seat bookings. */
    public AvailabilityContext(ZoneId zone, ServiceSpec service, int participants, ReservationRules rules,
                               List<DayInterval> businessHours, List<TimeRange> businessBlocks,
                               Map<Long, ResourceSpec> resources, Map<Long, List<DayInterval>> resourceHours,
                               Map<Long, List<TimeRange>> resourceBusy, Instant now) {
        this(zone, service, participants, rules, businessHours, businessBlocks, resources, resourceHours,
                resourceBusy, Map.of(), now, Map.of());
    }

    /** Whether the date has its own hours instead of the week's. */
    public boolean isSpecial(LocalDate date) {
        return specialHours.containsKey(date);
    }

    /** The business's hours on a date: the special ones when set, otherwise the week's. */
    public List<DayInterval> businessHoursOn(LocalDate date) {
        return specialHours.getOrDefault(date, businessHours);
    }

    public List<TimeRange> busyOf(Long resourceId) {
        return resourceBusy.getOrDefault(resourceId, List.of());
    }

    public List<SeatUse> seatsOf(Long resourceId) {
        return seatUse.getOrDefault(resourceId, List.of());
    }
}
