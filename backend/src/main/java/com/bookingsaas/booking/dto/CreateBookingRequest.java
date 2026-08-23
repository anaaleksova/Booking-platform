package com.bookingsaas.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * A booking taken by the business itself — phone, Instagram, walk-in.
 *
 * <p>The customer is either an existing record ({@code customerId}) or new
 * details to store ({@code customer}). {@code startDateTime} is wall-clock in
 * the business timezone, exactly as typed into the form.
 *
 * <p>{@code staffId} or {@code resourceId} left null means "any available" and
 * is resolved inside the booking transaction.
 */
public record CreateBookingRequest(
        @NotNull Long serviceId,
        Long staffId,
        Long resourceId,
        Long customerId,
        @Valid NewCustomer customer,
        @NotNull LocalDateTime startDateTime,
        @Size(max = 4000) String customerNotes,
        @Size(max = 4000) String internalNotes
) {

    public record NewCustomer(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 40) String phone,
            @Email @Size(max = 320) String email
    ) {
    }
}
