package com.bookingsaas.notification;

/**
 * An employee assigned to a reservation who can log in, and so hears about it:
 * new, moved, cancelled, or given to someone else.
 */
public record StaffMember(Long employeeId, String name, String email) {
}
