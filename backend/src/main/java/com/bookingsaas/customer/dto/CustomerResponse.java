package com.bookingsaas.customer.dto;

import com.bookingsaas.customer.Customer;
import com.bookingsaas.customer.spi.CustomerActivity;

import java.math.BigDecimal;
import java.time.Instant;

/** A customer together with the headline numbers of their history. */
public record CustomerResponse(
        Long id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        String notes,
        long reservationCount,
        BigDecimal totalSpent,
        Instant lastReservationAt,
        Instant createdAt
) {

    public static CustomerResponse from(Customer customer, CustomerActivity activity) {
        CustomerActivity stats = activity == null ? CustomerActivity.NONE : activity;
        return new CustomerResponse(customer.getId(), customer.getFirstName(), customer.getLastName(),
                customer.fullName(), customer.getEmail(), customer.getPhone(), customer.getNotes(),
                stats.reservationCount(), stats.totalSpent(), stats.lastReservationAt(), customer.getCreatedAt());
    }
}
