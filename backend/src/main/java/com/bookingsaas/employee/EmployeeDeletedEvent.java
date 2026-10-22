package com.bookingsaas.employee;

/**
 * An employee is being deleted. Published inside the deleting transaction, so
 * whatever hangs off the employee elsewhere (their login) goes with it — or
 * stays, if the delete fails.
 *
 * @param userId the login linked to the employee, or null
 */
public record EmployeeDeletedEvent(Long businessId, Long employeeId, Long userId) {
}
