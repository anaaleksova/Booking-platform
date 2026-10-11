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
    /** Fires the business's reminder lead time before the start. */
    public static final String RESERVATION_REMINDER_DUE = "reservation.reminder_due";
    /** Sent from the admin page to check the connection. */
    public static final String INTEGRATION_TEST = "integration.test";

    private IntegrationEventTypes() {
    }
}
