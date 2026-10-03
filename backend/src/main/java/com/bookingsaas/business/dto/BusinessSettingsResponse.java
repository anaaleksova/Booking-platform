package com.bookingsaas.business.dto;

import com.bookingsaas.business.BusinessSettings;

public record BusinessSettingsResponse(
        int minimumBookingNoticeMinutes,
        int maximumBookingAdvanceDays,
        int slotIntervalMinutes,
        boolean allowCustomerCancellation,
        int cancellationDeadlineHours,
        boolean emailNotificationsEnabled,
        boolean reminderNotificationsEnabled,
        int reminderLeadHours
) {

    public static BusinessSettingsResponse from(BusinessSettings settings) {
        return new BusinessSettingsResponse(
                settings.getMinimumBookingNoticeMinutes(),
                settings.getMaximumBookingAdvanceDays(),
                settings.getSlotIntervalMinutes(),
                settings.isAllowCustomerCancellation(),
                settings.getCancellationDeadlineHours(),
                settings.isEmailNotificationsEnabled(),
                settings.isReminderNotificationsEnabled(),
                settings.getReminderLeadHours());
    }
}
