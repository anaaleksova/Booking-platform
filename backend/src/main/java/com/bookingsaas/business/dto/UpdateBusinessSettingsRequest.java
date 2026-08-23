package com.bookingsaas.business.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Booking rules. Mirrors the CHECK constraints on {@code business_settings}. */
public record UpdateBusinessSettingsRequest(
        @NotNull @Min(0) @Max(43200) Integer minimumBookingNoticeMinutes,
        @NotNull @Min(1) @Max(730) Integer maximumBookingAdvanceDays,
        @NotNull @Min(5) @Max(480) Integer slotIntervalMinutes,
        @NotNull Boolean allowCustomerCancellation,
        @NotNull @Min(0) @Max(720) Integer cancellationDeadlineHours,
        @Min(1) @Max(1440) Integer defaultAppointmentDurationMinutes,
        @NotNull Boolean emailNotificationsEnabled,
        @NotNull Boolean reminderNotificationsEnabled,
        @NotNull @Min(1) @Max(168) Integer reminderLeadHours
) {
}
