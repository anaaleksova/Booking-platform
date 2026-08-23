package com.bookingsaas.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error identifiers returned to API clients.
 *
 * <p>The frontend branches on {@code code}, never on the human-readable
 * message, so messages can be reworded or localised freely.
 */
public enum ErrorCode {

    // --- generic ----------------------------------------------------------
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // --- auth / tenancy ---------------------------------------------------
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    NO_BUSINESS_CONTEXT(HttpStatus.FORBIDDEN),

    // --- business ---------------------------------------------------------
    SLUG_ALREADY_TAKEN(HttpStatus.CONFLICT),
    BUSINESS_INACTIVE(HttpStatus.FORBIDDEN),

    // --- catalogue --------------------------------------------------------
    SERVICE_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    SERVICE_IN_USE(HttpStatus.CONFLICT),
    STAFF_IN_USE(HttpStatus.CONFLICT),
    RESOURCE_IN_USE(HttpStatus.CONFLICT),
    CUSTOMER_IN_USE(HttpStatus.CONFLICT),
    DUPLICATE_CUSTOMER(HttpStatus.CONFLICT),

    // --- availability / booking ------------------------------------------
    BOOKING_NOT_AVAILABLE(HttpStatus.CONFLICT),
    BOOKING_OUTSIDE_WORKING_HOURS(HttpStatus.UNPROCESSABLE_ENTITY),
    BOOKING_TOO_SOON(HttpStatus.UNPROCESSABLE_ENTITY),
    BOOKING_TOO_FAR_IN_ADVANCE(HttpStatus.UNPROCESSABLE_ENTITY),
    BOOKING_IN_THE_PAST(HttpStatus.UNPROCESSABLE_ENTITY),
    STAFF_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_ENTITY),
    RESOURCE_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_ENTITY),
    STAFF_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY),
    RESOURCE_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY),
    NO_STAFF_AVAILABLE(HttpStatus.CONFLICT),
    NO_RESOURCE_AVAILABLE(HttpStatus.CONFLICT),
    INVALID_BOOKING_STATE(HttpStatus.UNPROCESSABLE_ENTITY),
    CANCELLATION_NOT_ALLOWED(HttpStatus.FORBIDDEN),
    CANCELLATION_DEADLINE_PASSED(HttpStatus.UNPROCESSABLE_ENTITY);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
