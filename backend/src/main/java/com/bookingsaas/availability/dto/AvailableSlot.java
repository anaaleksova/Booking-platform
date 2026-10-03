package com.bookingsaas.availability.dto;

import com.bookingsaas.availability.SlotEvaluation.RequirementOption;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/**
 * One reservable start time.
 *
 * <p>The free resources per requirement travel with the slot, so resource
 * selection needs no second round trip — although the final choice is always
 * re-validated inside the reservation transaction.
 */
public record AvailableSlot(
        LocalTime time,
        Instant startTime,
        Instant endTime,
        List<RequirementOption> options
) {
}
