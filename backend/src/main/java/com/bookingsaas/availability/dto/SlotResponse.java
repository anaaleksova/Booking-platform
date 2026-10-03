package com.bookingsaas.availability.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/**
 * One reservable start time with, per requirement, what is free.
 *
 * @param time business-local wall clock, ready to display
 */
public record SlotResponse(
        LocalTime time,
        Instant startTime,
        Instant endTime,
        List<SlotRequirement> requirements
) {

    /**
     * @param resources free resources, best fit first. On the customer-facing
     *                  API only filled for requirements the customer may choose.
     */
    public record SlotRequirement(
            Long requirementId,
            Long resourceTypeId,
            String resourceTypeName,
            int quantity,
            boolean required,
            boolean customerChoosable,
            List<ResourceOption> resources
    ) {
    }

    /** @param seatsLeft for a shared resource, the seats still free at this time; otherwise null */
    public record ResourceOption(Long id, String name, Integer capacity, Integer seatsLeft) {
    }
}
