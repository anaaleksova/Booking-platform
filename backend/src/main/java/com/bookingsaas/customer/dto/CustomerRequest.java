package com.bookingsaas.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Phone or email must be present — a reservation nobody can be contacted
 * about is not much use. Enforced in the service and by a CHECK constraint.
 */
public record CustomerRequest(
        @NotBlank @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Email @Size(max = 320) String email,
        @Pattern(regexp = "^$|^\\+?[0-9 ()./-]{6,40}$", message = "is not a valid phone number")
        @Size(max = 40) String phone,
        @Size(max = 4000) String notes
) {
}
