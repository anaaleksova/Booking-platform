package com.bookingsaas.common.domain;

/**
 * An owner saved part of their business's setup from the admin. The
 * "Getting started" checklist listens to tick off the matching step; the
 * modules that publish it know nothing about the checklist.
 */
public record SetupAreaEdited(Long businessId, Area area) {

    public enum Area {
        SERVICES,
        /** Resources and employees: the tables, rooms, desks and people that get booked. */
        RESOURCES,
        HOURS
    }
}
