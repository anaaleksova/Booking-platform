package com.bookingsaas.business.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Booking rules. Mirrors the CHECK constraints on {@code business_settings}.
 *
 * @param notificationEmail where new and cancelled reservations are announced;
 *                          empty for the owner's login email
 */
public record UpdateBusinessSettingsRequest(
        @NotNull @Min(0) @Max(43200) Integer minimumBookingNoticeMinutes,
        @NotNull @Min(1) @Max(730) Integer maximumBookingAdvanceDays,
        @NotNull @Min(5) @Max(480) Integer slotIntervalMinutes,
        @NotNull Boolean allowCustomerCancellation,
        @NotNull @Min(0) @Max(720) Integer cancellationDeadlineHours,
        @NotNull Boolean emailNotificationsEnabled,
        @NotNull Boolean reminderNotificationsEnabled,
        @NotNull @Min(1) @Max(168) Integer reminderLeadHours,
        @Email @Size(max = 320) String notificationEmail
) {
}
