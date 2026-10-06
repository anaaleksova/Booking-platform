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
     * @param resources   free resources, best fit first. On the customer-facing
     *                    API only filled for requirements the customer may choose.
     * @param combinable  whether resources are combined for larger groups;
     *                    {@code quantity} is then a minimum
     * @param allocations for a combinable requirement, the best ways to seat the
     *                    group at this time, best first — one resource or several.
     *                    Filled like {@code resources}.
     */
    public record SlotRequirement(
            Long requirementId,
            Long resourceTypeId,
            String resourceTypeName,
            int quantity,
            boolean required,
            boolean customerChoosable,
            boolean combinable,
            List<ResourceOption> resources,
            List<AllocationOption> allocations
    ) {
    }

    /** @param seatsLeft for a shared resource, the seats still free at this time; otherwise null */
    public record ResourceOption(Long id, String name, Integer capacity, Integer seatsLeft) {
    }

    /**
     * One way to seat the group. Choosing it means sending its resource ids as
     * the requirement's selection.
     *
     * @param capacity combined capacity as seen at this time (free seats for a
     *                 shared resource); {@code null} when a resource is not
     *                 limited by participants
     * @param area     the floor plan the resources stand on, if any
     */
    public record AllocationOption(List<ResourceOption> resources, Integer capacity, String area) {
    }
}
