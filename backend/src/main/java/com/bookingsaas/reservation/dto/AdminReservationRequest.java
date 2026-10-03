package com.bookingsaas.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A reservation taken by the business itself — phone, walk-in, message.
 *
 * <p>The customer is either an existing record ({@code customerId}) or new
 * details. Lead-time rules do not apply; opening hours and conflicts do.
 * The admin may pick resources for any requirement.
 */
public record AdminReservationRequest(
        @NotNull Long serviceId,
        @NotNull @Min(1) Integer participants,
        @NotNull LocalDateTime startTime,
        @Size(max = 20) List<@Valid ResourceSelection> resourceSelections,
        @Size(max = 40) String discountCode,
        Long customerId,
        @Valid NewCustomer customer,
        @Size(max = 4000) String customerNotes,
        @Size(max = 4000) String internalNotes
) {

    /** A new customer entered with the reservation: email and phone are both required. */
    public record NewCustomer(
            @NotBlank @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "^\\+?[0-9 ()./-]{6,40}$", message = "is not a valid phone number")
            @Size(max = 40) String phone
    ) {
    }
}
