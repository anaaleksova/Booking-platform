package com.bookingsaas.availability;

/**
 * A candidate resource as the engine sees it.
 *
 * @param capacity     participants served at once; {@code null} means participants
 *                     do not limit this resource
 * @param shared       several bookings may use it at once until its seats are
 *                     taken — a party of two at a shared eight-seat table leaves
 *                     six seats for others
 * @param minPartySize the smallest group a private resource is given to;
 *                     {@code null} for any group
 */
public record ResourceSpec(Long id, Long resourceTypeId, String name, Integer capacity, boolean shared,
                           Integer minPartySize) {

    /** A private resource with no minimum group size. */
    public ResourceSpec(Long id, Long resourceTypeId, String name, Integer capacity) {
        this(id, resourceTypeId, name, capacity, false, null);
    }

    /** Capacity for arithmetic: an unlimited resource covers any group. */
    long effectiveCapacity() {
        return capacity == null ? Integer.MAX_VALUE : capacity;
    }

    /** The same resource with only {@code seats} left, as seen at one time. */
    ResourceSpec withCapacity(int seats) {
        return new ResourceSpec(id, resourceTypeId, name, seats, shared, minPartySize);
    }
}
