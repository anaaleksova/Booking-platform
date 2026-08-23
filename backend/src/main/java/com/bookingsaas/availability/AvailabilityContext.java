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
 * @param staffBusy    per staff member, the windows they are already taken —
 *                     bookings (buffers included) and blocked time merged,
 *                     because the engine treats them identically
 * @param staffHours   per staff member, their own weekly schedule. A member
 *                     absent from this map follows the business hours; a member
 *                     present but with no interval on a given day is off that
 *                     day
 */
public record AvailabilityContext(
        ZoneId zone,
        ServiceSpec service,
        BookingRules rules,
        List<DayInterval> businessHours,
        List<TimeRange> businessBlocks,
        List<Long> staffIds,
        Map<Long, List<DayInterval>> staffHours,
        Map<Long, List<TimeRange>> staffBusy,
        List<Long> resourceIds,
        Map<Long, List<TimeRange>> resourceBusy,
        Instant now
) {

    public List<TimeRange> busyFor(Map<Long, List<TimeRange>> source, Long id) {
        return source.getOrDefault(id, List.of());
    }
}
