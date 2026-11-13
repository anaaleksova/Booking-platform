package com.bookingsaas.integration.events;

/**
 * Names of the events sent to the automation layer. Strings rather than an
 * enum, and namespaced, so new events (customer.created, discount.created,
 * daily summaries...) are added without a schema change.
 */
public final class IntegrationEventTypes {

    public static final String RESERVATION_CREATED = "reservation.created";
    public static final String RESERVATION_CANCELLED = "reservation.cancelled";
    public static final String RESERVATION_RESCHEDULED = "reservation.rescheduled";
    /** A booking request waiting for the owner's approval; the customer was already told it is a request. */
    public static final String RESERVATION_REQUESTED = "reservation.requested";
    /** The owner approved a request: the customer should now hear it is confirmed. */
    public static final String RESERVATION_APPROVED = "reservation.approved";
    /** The owner declined a request. */
    public static final String RESERVATION_DECLINED = "reservation.declined";
    /** Fires the business's reminder lead time before the start. */
    public static final String RESERVATION_REMINDER_DUE = "reservation.reminder_due";
    /** Sent from the admin page to check the connection. */
    public static final String INTEGRATION_TEST = "integration.test";

    private IntegrationEventTypes() {
    }
}
