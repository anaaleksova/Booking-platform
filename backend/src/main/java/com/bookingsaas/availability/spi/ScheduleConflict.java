package com.bookingsaas.availability.spi;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A booking still ahead that a schedule change would leave stranded: it sits in
 * new time off, in a closure, or outside a date's special hours.
 *
 * @param resources the assigned resources' names, for display
 */
public record ScheduleConflict(
        Long reservationId,
        String customerName,
        String serviceName,
        int participants,
        Instant startTime,
        Instant endTime,
        LocalDateTime localStartTime,
        LocalDateTime localEndTime,
        List<String> resources
) {
}
