package com.bookingsaas.reservation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contact details entered while booking. Email and phone are both required,
 * so the business can always reach the customer. A returning customer is
 * recognised by either rather than duplicated.
 */
public record CustomerDetails(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "^\\+?[0-9 ()./-]{6,40}$", message = "is not a valid phone number")
        @Size(max = 40) String phone
) {
}
