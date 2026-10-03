package com.bookingsaas.availability;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.domain.TimeRange;

import java.time.Instant;
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
        Instant now
) {

    /** A context without any shared-seat bookings. */
    public AvailabilityContext(ZoneId zone, ServiceSpec service, int participants, ReservationRules rules,
                               List<DayInterval> businessHours, List<TimeRange> businessBlocks,
                               Map<Long, ResourceSpec> resources, Map<Long, List<DayInterval>> resourceHours,
                               Map<Long, List<TimeRange>> resourceBusy, Instant now) {
        this(zone, service, participants, rules, businessHours, businessBlocks, resources, resourceHours,
                resourceBusy, Map.of(), now);
    }

    public List<TimeRange> busyOf(Long resourceId) {
        return resourceBusy.getOrDefault(resourceId, List.of());
    }

    public List<SeatUse> seatsOf(Long resourceId) {
        return seatUse.getOrDefault(resourceId, List.of());
    }
}
