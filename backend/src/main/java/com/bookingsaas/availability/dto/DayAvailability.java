package com.bookingsaas.availability.dto;

import java.time.LocalDate;
import java.util.List;

/** The engine's view of one day's slots, before names are attached for the API. */
public record DayAvailability(LocalDate date, List<AvailableSlot> slots) {

    public static DayAvailability empty(LocalDate date) {
        return new DayAvailability(date, List.of());
    }
}
