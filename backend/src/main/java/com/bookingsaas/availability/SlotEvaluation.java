package com.bookingsaas.availability;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;

import java.util.List;

/**
 * The verdict on one exact start time: bookable, or why not.
 *
 * <p>Carrying the reason lets the booking API answer "outside working hours"
 * or "no staff available" instead of a flat "unavailable", which is the
 * difference between a customer retrying successfully and giving up.
 */
public record SlotEvaluation(
        boolean available,
        List<Long> staffIds,
        List<Long> resourceIds,
        ErrorCode reason,
        String message
) {

    public static SlotEvaluation available(List<Long> staffIds, List<Long> resourceIds) {
        return new SlotEvaluation(true, staffIds, resourceIds, null, null);
    }

    public static SlotEvaluation rejected(ErrorCode reason, String message) {
        return new SlotEvaluation(false, List.of(), List.of(), reason, message);
    }

    /** Throws the domain error the evaluation carries. */
    public ApiException toException() {
        return ApiException.of(reason == null ? ErrorCode.BOOKING_NOT_AVAILABLE : reason,
                message == null ? "The selected time is not available." : message);
    }
}
