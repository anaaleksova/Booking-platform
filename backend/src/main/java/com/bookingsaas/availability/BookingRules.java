package com.bookingsaas.availability;

import com.bookingsaas.business.BusinessSettings;

import java.time.Duration;

/**
 * The tenant's booking policy, as the engine sees it.
 *
 * <p>{@code enforceLeadTimes} is false for bookings taken by the business
 * itself: an owner writing down the walk-in standing at the counter must not be
 * refused for "two hours' notice required". Conflict and working-hours rules
 * still apply — those protect the schedule, not the customer.
 */
public record BookingRules(
        Duration slotInterval,
        Duration minimumNotice,
        int maximumAdvanceDays,
        boolean enforceLeadTimes
) {

    public static BookingRules publicBooking(BusinessSettings settings) {
        return new BookingRules(settings.slotInterval(), settings.minimumNotice(),
                settings.getMaximumBookingAdvanceDays(), true);
    }

    public static BookingRules internalBooking(BusinessSettings settings) {
        return new BookingRules(settings.slotInterval(), settings.minimumNotice(),
                settings.getMaximumBookingAdvanceDays(), false);
    }
}
