package com.bookingsaas.availability.dto;

import com.bookingsaas.business.dto.SpecialHours;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One date's hours. No (or empty) {@code hours} closes the business that day;
 * several spans make a break, as in the weekly hours.
 */
public record SpecialDayRequest(
        @Size(max = 6) @Valid List<SpecialHours.Span> hours,
        @Size(max = 255) String note
) {
}
