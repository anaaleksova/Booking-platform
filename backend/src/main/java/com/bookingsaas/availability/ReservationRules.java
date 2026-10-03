package com.bookingsaas.availability;

import com.bookingsaas.business.BusinessSettings;

import java.time.Duration;

/**
 * The tenant's booking policy, as the engine sees it.
 *
 * <p>{@code enforceLeadTimes} is false for reservations taken by the business
 * itself: an owner writing down the walk-in standing at the counter must not be
 * refused for "one hour's notice required". Conflict and opening-hours rules
 * still apply — those protect the schedule, not the customer.
 */
public record ReservationRules(
        Duration slotInterval,
        Duration minimumNotice,
        int maximumAdvanceDays,
        boolean enforceLeadTimes
) {

    public static ReservationRules customerFacing(BusinessSettings settings) {
        return new ReservationRules(settings.slotInterval(), settings.minimumNotice(),
                settings.getMaximumBookingAdvanceDays(), true);
    }

    public static ReservationRules internal(BusinessSettings settings) {
        return new ReservationRules(settings.slotInterval(), settings.minimumNotice(),
                settings.getMaximumBookingAdvanceDays(), false);
    }
}
