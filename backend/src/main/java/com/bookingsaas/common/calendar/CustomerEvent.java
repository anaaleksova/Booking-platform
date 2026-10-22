package com.bookingsaas.common.calendar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The event a customer adds to their own calendar: what, where, and the link
 * to manage it. Built in one place, so the email attachment, the email's
 * buttons and the reservation page all describe the same event.
 */
public final class CustomerEvent {

    private CustomerEvent() {
    }

    public static Ics.Event of(String reference, String businessName, String address, String phone,
                               String serviceName, String with, Instant start, Instant end, boolean cancelled,
                               String frontendUrl) {
        String link = frontendUrl + "/booking/" + reference;
        List<String> lines = new ArrayList<>();
        if (with != null && !with.isBlank()) {
            lines.add("With: " + with);
        }
        if (phone != null && !phone.isBlank()) {
            lines.add(businessName + ": " + phone);
        }
        lines.add("View or manage your reservation: " + link);
        return new Ics.Event(uid(reference), start, end, serviceName + " at " + businessName,
                String.join("\n", lines), address, link, cancelled, 0);
    }

    /** Shared by the customer's event and the owner's feed: the same reservation is the same event. */
    public static String uid(String reference) {
        return reference + "@booking-platform";
    }

    /** Where the customer's .ics file is served. */
    public static String fileUrl(String frontendUrl, String reference) {
        return frontendUrl + "/api/public/reservations/" + reference + "/calendar.ics";
    }
}
