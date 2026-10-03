package com.bookingsaas.availability;

import java.time.Instant;
import java.util.List;

/**
 * A validated reservation slot with the concrete resources actually chosen.
 *
 * <p>Produced inside the reservation transaction: this is where "any free
 * room" stops being a wish and becomes Room 2.
 *
 * @param blockStart start of the reserved window including the before-buffer
 * @param blockEnd   end of the reserved window including the after-buffer
 */
public record ResourceAllocation(
        Instant start,
        Instant end,
        Instant blockStart,
        Instant blockEnd,
        List<AllocatedResource> resources
) {

    /**
     * One resource and the requirement it satisfies.
     *
     * @param seats seats held on a shared resource; {@code null} when the
     *              whole resource is taken
     */
    public record AllocatedResource(Long requirementId, Long resourceId, Integer seats) {

        public AllocatedResource(Long requirementId, Long resourceId) {
            this(requirementId, resourceId, null);
        }
    }

    public List<Long> resourceIds() {
        return resources.stream().map(AllocatedResource::resourceId).toList();
    }
}
