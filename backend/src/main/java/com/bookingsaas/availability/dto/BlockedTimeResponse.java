package com.bookingsaas.availability.dto;

import com.bookingsaas.availability.BlockedTime;
import com.bookingsaas.availability.spi.ScheduleConflict;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Carries both the absolute instant and the business-local wall clock.
 *
 * @param enteredByStaff the employee entered this time off themselves
 * @param conflicts      bookings still in the blocked time, which need moving or cancelling
 */
public record BlockedTimeResponse(
        Long id,
        Long resourceId,
        String resourceName,
        Instant startTime,
        Instant endTime,
        LocalDateTime localStartTime,
        LocalDateTime localEndTime,
        String reason,
        boolean enteredByStaff,
        List<ScheduleConflict> conflicts
) {

    public static BlockedTimeResponse from(BlockedTime blocked, ZoneId zone, String resourceName,
                                           boolean enteredByStaff, List<ScheduleConflict> conflicts) {
        return new BlockedTimeResponse(
                blocked.getId(), blocked.getResourceId(), resourceName,
                blocked.getStartTime(), blocked.getEndTime(),
                LocalDateTime.ofInstant(blocked.getStartTime(), zone),
                LocalDateTime.ofInstant(blocked.getEndTime(), zone),
                blocked.getReason(), enteredByStaff, conflicts);
    }
}
