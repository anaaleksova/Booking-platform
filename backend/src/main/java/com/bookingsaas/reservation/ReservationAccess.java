package com.bookingsaas.reservation;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.employee.Employee;
import com.bookingsaas.employee.EmployeeService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The role-level filter on top of tenant isolation: owners see the whole
 * business, a STAFF user only the reservations their own employee resource is
 * assigned to.
 */
@Component
public class ReservationAccess {

    private final CurrentTenant currentTenant;
    private final EmployeeService employeeService;

    public ReservationAccess(CurrentTenant currentTenant, EmployeeService employeeService) {
        this.currentTenant = currentTenant;
        this.employeeService = employeeService;
    }

    public void requireAccess(Long businessId, Reservation reservation) {
        if (currentTenant.isOwner()) {
            return;
        }
        Long ownResource = ownResourceId(businessId).orElse(null);
        if (ownResource == null || !reservation.resourceIds().contains(ownResource)) {
            throw ApiException.accessDenied("You can only manage your own reservations.");
        }
    }

    /**
     * The resource a list must be restricted to: the caller's own for STAFF,
     * whatever was requested for an owner.
     */
    public Long effectiveResourceFilter(Long businessId, Long requestedResourceId) {
        if (currentTenant.isOwner()) {
            return requestedResourceId;
        }
        Long own = ownResourceId(businessId)
                .orElseThrow(() -> ApiException.accessDenied("No employee profile is linked to this account."));
        if (requestedResourceId != null && !requestedResourceId.equals(own)) {
            throw ApiException.accessDenied("You can only view your own reservations.");
        }
        return own;
    }

    private Optional<Long> ownResourceId(Long businessId) {
        return employeeService.employeeOf(businessId, currentTenant.userId()).map(Employee::getResourceId);
    }
}
