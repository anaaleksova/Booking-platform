package com.bookingsaas.availability;

import java.time.Instant;

/**
 * A validated booking slot with the staff member and resource actually chosen.
 *
 * <p>Produced inside the booking transaction: this is where "any available"
 * stops being a wish and becomes a specific assignment.
 *
 * @param blockStart start of the reserved window including the before-buffer
 * @param blockEnd   end of the reserved window including the after-buffer
 */
public record SlotAssignment(
        Long staffId,
        Long resourceId,
        Instant start,
        Instant end,
        Instant blockStart,
        Instant blockEnd
) {
}
