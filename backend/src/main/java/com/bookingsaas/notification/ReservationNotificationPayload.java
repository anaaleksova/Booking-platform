package com.bookingsaas.notification;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Everything a message needs about a reservation, flattened.
 *
 * <p>The reservation module fills this in and publishes it; the notification module
 * never queries reservation tables. That is what keeps "send an email" out of the
 * reservation transaction's dependency graph — and what will let a future SMS
 * sender reuse the identical input.
 */
public record ReservationNotificationPayload(
        Long businessId,
        Long reservationId,
        String businessName,
        String businessEmail,
        String businessPhone,
        String businessAddress,
        String businessTimezone,
        String customerName,
        String customerEmail,
        String serviceName,
        int participants,
        /** Assigned resources, e.g. "Room 2, Anna, Maria". */
        String resourceSummary,
        Instant start,
        Instant end,
        String publicReference,
        /** Free text: a cancellation reason, or the previous time on a reschedule. */
        String detail
) {

    public ZoneId zone() {
        return businessTimezone == null ? ZoneId.of("UTC") : ZoneId.of(businessTimezone);
    }

    public boolean hasCustomerEmail() {
        return customerEmail != null && !customerEmail.isBlank();
    }

    public boolean hasBusinessEmail() {
        return businessEmail != null && !businessEmail.isBlank();
    }
}
