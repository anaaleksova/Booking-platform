package com.bookingsaas.business;

import com.bookingsaas.common.domain.DayInterval;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * What kind of business an owner signed up as. It picks the starting setup
 * (see {@code onboarding.BusinessTemplates}): the opening week and the slot
 * grid here, the resources and services there. Everything stays editable.
 */
public enum BusinessType {

    RESTAURANT("Restaurant / Bar / Café", week(1, 7, 12, 0, 23, 0), 30),
    HAIR_SALON("Hair salon / Barber", week(1, 6, 9, 0, 19, 0), 15),
    NAILS_BEAUTY("Nails / Beauty", week(1, 6, 9, 0, 19, 0), 15),
    SPA("Spa / Massage", week(1, 6, 10, 0, 20, 0), 30),
    FITNESS("Fitness / Classes", join(week(1, 5, 7, 0, 21, 0), week(6, 6, 9, 0, 14, 0)), 60),
    COWORKING("Co-working", week(1, 5, 8, 0, 20, 0), 30),
    /** Starts empty apart from a Mon–Fri week, as every business did before templates. */
    OTHER("Something else", week(1, 5, 9, 0, 17, 0), 30);

    private final String label;
    private final List<DayInterval> openingHours;
    private final int slotIntervalMinutes;

    BusinessType(String label, List<DayInterval> openingHours, int slotIntervalMinutes) {
        this.label = label;
        this.openingHours = openingHours;
        this.slotIntervalMinutes = slotIntervalMinutes;
    }

    public String label() {
        return label;
    }

    public List<DayInterval> openingHours() {
        return openingHours;
    }

    /** Events (a band, a quiz night) belong to restaurants and bars; a salon has no use for them. */
    public boolean hasEvents() {
        return this == RESTAURANT;
    }

    /** How far apart offered start times are; 60 for classes, so they start on the hour. */
    public int slotIntervalMinutes() {
        return slotIntervalMinutes;
    }

    /** The same hours on each ISO day from {@code firstDay} to {@code lastDay}. */
    private static List<DayInterval> week(int firstDay, int lastDay, int fromHour, int fromMinute,
                                          int toHour, int toMinute) {
        List<DayInterval> days = new ArrayList<>();
        for (int day = firstDay; day <= lastDay; day++) {
            days.add(new DayInterval(day, LocalTime.of(fromHour, fromMinute), LocalTime.of(toHour, toMinute)));
        }
        return List.copyOf(days);
    }

    private static List<DayInterval> join(List<DayInterval> first, List<DayInterval> second) {
        List<DayInterval> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }
}
