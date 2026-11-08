package com.bookingsaas.availability.dto;

import com.bookingsaas.availability.spi.ScheduleConflict;
import com.bookingsaas.business.dto.SpecialHours;

import java.time.LocalDate;
import java.util.List;

/**
 * A special date for the admin panel.
 *
 * @param conflicts bookings on that date outside its hours, which need moving or cancelling
 */
public record SpecialDayResponse(
        LocalDate date,
        boolean closed,
        List<SpecialHours.Span> hours,
        String note,
        List<ScheduleConflict> conflicts
) {

    public static SpecialDayResponse from(SpecialHours day, List<ScheduleConflict> conflicts) {
        return new SpecialDayResponse(day.date(), day.closed(), day.hours(), day.note(), conflicts);
    }
}
