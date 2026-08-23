package com.bookingsaas.availability.dto;

import com.bookingsaas.availability.BlockedTime;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Carries both the absolute instant and the business-local wall clock. */
public record BlockedTimeResponse(
        Long id,
        Long staffId,
        String staffName,
        Long resourceId,
        String resourceName,
        Instant startDateTime,
        Instant endDateTime,
        LocalDateTime localStartDateTime,
        LocalDateTime localEndDateTime,
        String reason
) {

    public static BlockedTimeResponse from(BlockedTime blocked, ZoneId zone, String staffName, String resourceName) {
        return new BlockedTimeResponse(
                blocked.getId(), blocked.getStaffId(), staffName, blocked.getResourceId(), resourceName,
                blocked.getStartDateTime(), blocked.getEndDateTime(),
                LocalDateTime.ofInstant(blocked.getStartDateTime(), zone),
                LocalDateTime.ofInstant(blocked.getEndDateTime(), zone),
                blocked.getReason());
    }
}
