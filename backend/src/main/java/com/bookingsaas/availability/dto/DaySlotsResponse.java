package com.bookingsaas.availability.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * A day's reservable slots. Days with none are still returned, so a date
 * picker can grey them out instead of guessing.
 */
public record DaySlotsResponse(LocalDate date, List<SlotResponse> slots) {
}
