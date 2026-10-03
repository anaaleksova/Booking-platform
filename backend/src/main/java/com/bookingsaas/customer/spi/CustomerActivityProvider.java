package com.bookingsaas.customer.spi;

import java.util.Collection;
import java.util.Map;

/**
 * What the customer list needs to know about reservations.
 *
 * <p>Declared here and implemented by the reservation module, so the customer
 * module never depends on reservations — the same inversion the availability
 * engine uses for booked intervals.
 */
public interface CustomerActivityProvider {

    /** Activity per customer id; customers with no reservations may be absent. */
    Map<Long, CustomerActivity> activityOf(Long businessId, Collection<Long> customerIds);
}
