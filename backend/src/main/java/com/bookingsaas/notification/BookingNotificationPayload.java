package com.bookingsaas.notification;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Everything a message needs about a booking, flattened.
 *
 * <p>The booking module fills this in and publishes it; the notification module
 * never queries booking tables. That is what keeps "send an email" out of the
 * booking transaction's dependency graph — and what will let a future SMS
 * sender reuse the identical input.
 */
public record BookingNotificationPayload(
        Long businessId,
        Long bookingId,
        String businessName,
        String businessEmail,
        String businessPhone,
        String businessAddress,
        String businessTimezone,
        String customerName,
        String customerEmail,
        String serviceName,
        String staffName,
        String resourceName,
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
