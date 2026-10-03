package com.bookingsaas.availability.dto;

import com.bookingsaas.availability.BlockedTime;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Carries both the absolute instant and the business-local wall clock. */
public record BlockedTimeResponse(
        Long id,
        Long resourceId,
        String resourceName,
        Instant startTime,
        Instant endTime,
        LocalDateTime localStartTime,
        LocalDateTime localEndTime,
        String reason
) {

    public static BlockedTimeResponse from(BlockedTime blocked, ZoneId zone, String resourceName) {
        return new BlockedTimeResponse(
                blocked.getId(), blocked.getResourceId(), resourceName,
                blocked.getStartTime(), blocked.getEndTime(),
                LocalDateTime.ofInstant(blocked.getStartTime(), zone),
                LocalDateTime.ofInstant(blocked.getEndTime(), zone),
                blocked.getReason());
    }
}
