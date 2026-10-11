package com.bookingsaas.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error identifiers returned to API clients.
 *
 * <p>The frontend branches on {@code error}, never on the human-readable
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
    /** The emailed sign-up code is wrong. */
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST),
    /** No sign-up is waiting for this email, it expired, or it ran out of attempts. */
    VERIFICATION_EXPIRED(HttpStatus.GONE),
    /** A new code was asked for too soon after the last one. */
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    /** The verification email could not be sent. */
    EMAIL_DELIVERY_FAILED(HttpStatus.BAD_GATEWAY),

    // --- business ---------------------------------------------------------
    SLUG_ALREADY_TAKEN(HttpStatus.CONFLICT),

    // --- catalogue --------------------------------------------------------
    SERVICE_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    SERVICE_IN_USE(HttpStatus.CONFLICT),
    RESOURCE_TYPE_IN_USE(HttpStatus.CONFLICT),
    DUPLICATE_RESOURCE_TYPE(HttpStatus.CONFLICT),
    RESOURCE_IN_USE(HttpStatus.CONFLICT),
    EMPLOYEE_IN_USE(HttpStatus.CONFLICT),
    CUSTOMER_IN_USE(HttpStatus.CONFLICT),
    DUPLICATE_CUSTOMER(HttpStatus.CONFLICT),

    // --- availability / reservations -------------------------------------
    /** A resource needed for the reservation is taken — the classic double-booking case. */
    RESOURCE_NOT_AVAILABLE(HttpStatus.CONFLICT),
    OUTSIDE_BUSINESS_HOURS(HttpStatus.UNPROCESSABLE_ENTITY),
    RESERVATION_IN_THE_PAST(HttpStatus.UNPROCESSABLE_ENTITY),
    RESERVATION_TOO_SOON(HttpStatus.UNPROCESSABLE_ENTITY),
    RESERVATION_TOO_FAR_IN_ADVANCE(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_PARTICIPANTS(HttpStatus.BAD_REQUEST),
    INVALID_RESOURCE_SELECTION(HttpStatus.UNPROCESSABLE_ENTITY),
    RESOURCE_SELECTION_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_RESERVATION_STATE(HttpStatus.UNPROCESSABLE_ENTITY),
    CANCELLATION_NOT_ALLOWED(HttpStatus.FORBIDDEN),
    CANCELLATION_DEADLINE_PASSED(HttpStatus.UNPROCESSABLE_ENTITY),

    // --- integrations -----------------------------------------------------
    /** An Idempotency-Key sent again with a different request body. */
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY),

    // --- discounts --------------------------------------------------------
    DUPLICATE_DISCOUNT_CODE(HttpStatus.CONFLICT),
    DISCOUNT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    DISCOUNT_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    DISCOUNT_NOT_STARTED(HttpStatus.UNPROCESSABLE_ENTITY),
    DISCOUNT_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY),
    DISCOUNT_USAGE_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY),
    DISCOUNT_MINIMUM_NOT_MET(HttpStatus.UNPROCESSABLE_ENTITY),
    DISCOUNT_NOT_APPLICABLE(HttpStatus.UNPROCESSABLE_ENTITY);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
