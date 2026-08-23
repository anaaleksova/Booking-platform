package com.bookingsaas.customer.dto;

import com.bookingsaas.customer.Customer;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String name,
        String phone,
        String email,
        String notes,
        Instant createdAt
) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getPhone(),
                customer.getEmail(), customer.getNotes(), customer.getCreatedAt());
    }
}
