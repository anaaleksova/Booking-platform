package com.bookingsaas.availability;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;

import java.util.List;
import java.util.Map;

/**
 * The verdict on one exact start time: reservable, or why not.
 *
 * <p>Carrying the reason lets the API answer "outside business hours" or "no
 * treatment room is free" instead of a flat "unavailable", which is the
 * difference between a customer retrying successfully and giving up.
 *
 * @param options per requirement, the resources free at that time, best fit
 *                first — what a customer may choose from, and what automatic
 *                assignment picks from
 */
public record SlotEvaluation(
        boolean available,
        List<RequirementOption> options,
        ErrorCode reason,
        String message
) {

    /**
     * @param candidateIds free, eligible resources for the requirement, ordered
     *                     by preference (smallest adequate capacity, then least
     *                     busy)
     * @param satisfiable  whether {@code quantity} of them can cover the group
     * @param seatsLeft    for shared candidates, the seats still free at that time
     * @param allocations  for a combinable requirement, the best ways to seat
     *                     the group at that time, best first; empty otherwise
     */
    public record RequirementOption(RequirementSpec requirement, List<Long> candidateIds, boolean satisfiable,
                                    Map<Long, Integer> seatsLeft, List<AllocationFinder.Allocation> allocations) {

        public RequirementOption(RequirementSpec requirement, List<Long> candidateIds, boolean satisfiable,
                                 Map<Long, Integer> seatsLeft) {
            this(requirement, candidateIds, satisfiable, seatsLeft, List.of());
        }

        public RequirementOption(RequirementSpec requirement, List<Long> candidateIds, boolean satisfiable) {
            this(requirement, candidateIds, satisfiable, Map.of(), List.of());
        }
    }

    public static SlotEvaluation available(List<RequirementOption> options) {
        return new SlotEvaluation(true, options, null, null);
    }

    public static SlotEvaluation rejected(ErrorCode reason, String message) {
        return new SlotEvaluation(false, List.of(), reason, message);
    }

    /** Throws the domain error the evaluation carries. */
    public ApiException toException() {
        return ApiException.of(reason == null ? ErrorCode.RESOURCE_NOT_AVAILABLE : reason,
                message == null ? "The selected time is not available." : message);
    }
}
