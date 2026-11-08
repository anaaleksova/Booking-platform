package com.bookingsaas.business.dto;

import com.bookingsaas.business.SpecialDay;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * One date's special opening hours, as customers and the admin read them.
 * {@code closed} is true when {@code hours} is empty.
 */
public record SpecialHours(LocalDate date, boolean closed, List<Span> hours, String note) {

    public record Span(@NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                       @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
    }

    public static SpecialHours from(SpecialDay day) {
        return new SpecialHours(day.getDay(), day.isClosed(),
                day.getHours().stream().map(h -> new Span(h.getStartTime(), h.getEndTime())).toList(),
                day.getNote());
    }
}
