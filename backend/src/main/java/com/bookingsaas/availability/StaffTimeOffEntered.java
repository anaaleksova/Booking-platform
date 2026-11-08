package com.bookingsaas.availability;

import com.bookingsaas.availability.spi.ScheduleConflict;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A staff member put time off in their own calendar. The owner is told, and
 * especially about the bookings that now need someone else or another time:
 * staff cannot move or cancel bookings themselves.
 */
public record StaffTimeOffEntered(
        Long businessId,
        String businessName,
        String employeeName,
        LocalDateTime start,
        LocalDateTime end,
        String reason,
        List<ScheduleConflict> conflicts
) {
}
