package com.bookingsaas.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * @param endTime   optional; earlier than the start when the event runs past midnight
 * @param showFrom  first day on the booking page; empty = a week before the event
 * @param showUntil last day on the booking page; empty = the day of the event
 * @param serviceId what customers book for the event; empty = an announcement only
 */
public record EventRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 4000) String description,
        @Size(max = 160) String performer,
        UUID imageMediaId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        LocalTime endTime,
        LocalDate showFrom,
        LocalDate showUntil,
        Long serviceId
) {
}
