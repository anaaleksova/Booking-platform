package com.bookingsaas.common.error;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single error shape every endpoint returns.
 *
 * <pre>
 * {
 *   "timestamp": "2026-08-16T10:15:30Z",
 *   "status": 409,
 *   "error": "RESOURCE_NOT_AVAILABLE",
 *   "message": "The selected resources are no longer available.",
 *   "path": "/api/public/businesses/serenity-studio/reservations",
 *   "fieldErrors": { "durationMinutes": "must be greater than 0" }
 * }
 * </pre>
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        /* Stable machine-readable code; clients branch on this, never on the message. */
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors,
        List<String> details
) {

    public static ApiErrorResponse of(ErrorCode code, String message, String path) {
        return new ApiErrorResponse(Instant.now(), code.status().value(), code.name(), message, path, null, null);
    }

    public static ApiErrorResponse validation(String message, String path, Map<String, String> fieldErrors) {
        return new ApiErrorResponse(Instant.now(), ErrorCode.VALIDATION_FAILED.status().value(),
                ErrorCode.VALIDATION_FAILED.name(), message, path, fieldErrors, null);
    }
}
