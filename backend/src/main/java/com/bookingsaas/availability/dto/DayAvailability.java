package com.bookingsaas.availability.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * A day's bookable slots. Days with none are still returned, so a date picker
 * can grey them out instead of guessing.
 */
public record DayAvailability(LocalDate date, List<AvailableSlot> slots) {

    public boolean hasAvailability() {
        return !slots.isEmpty();
    }

    public static DayAvailability empty(LocalDate date) {
        return new DayAvailability(date, List.of());
    }
}
