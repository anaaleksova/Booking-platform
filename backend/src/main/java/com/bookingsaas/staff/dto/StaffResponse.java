package com.bookingsaas.staff.dto;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.staff.Staff;

import java.util.List;

public record StaffResponse(
        Long id,
        String name,
        String email,
        String phone,
        String photoUrl,
        boolean active,
        boolean hasLoginAccount,
        List<Long> serviceIds,
        List<DayInterval> workingHours
) {

    public static StaffResponse from(Staff staff, List<Long> serviceIds, List<DayInterval> workingHours) {
        return new StaffResponse(staff.getId(), staff.getName(), staff.getEmail(), staff.getPhone(),
                staff.getPhotoUrl(), staff.isActive(), staff.getUserId() != null, serviceIds, workingHours);
    }
}
