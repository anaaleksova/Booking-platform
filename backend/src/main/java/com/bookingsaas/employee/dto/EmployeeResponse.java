package com.bookingsaas.employee.dto;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.employee.Employee;
import com.bookingsaas.media.Media;

import java.util.List;
import java.util.UUID;

public record EmployeeResponse(
        Long id,
        /* The employee's resource: what reservations and blocked time refer to. */
        Long resourceId,
        String name,
        String email,
        String phone,
        UUID imageMediaId,
        String imageUrl,
        boolean active,
        Integer capacity,
        boolean hasLoginAccount,
        List<Long> serviceIds,
        /* Own weekly schedule; empty = works whenever the business is open. */
        List<DayInterval> workingHours
) {

    public static EmployeeResponse from(Employee employee, Integer capacity, UUID imageMediaId,
                                        List<Long> serviceIds, List<DayInterval> workingHours) {
        return new EmployeeResponse(employee.getId(), employee.getResourceId(), employee.getName(),
                employee.getEmail(), employee.getPhone(), imageMediaId, Media.urlOf(imageMediaId),
                employee.isActive(), capacity,
                employee.getUserId() != null, serviceIds, workingHours);
    }
}
