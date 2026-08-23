package com.bookingsaas.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sign-up creates the account and its first business in one step: a booking
 * platform account without a business could not do anything anyway.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 128, message = "must be at least 8 characters") String password,
        @NotBlank @Size(max = 160) String businessName,
        /* Optional; defaults to UTC when the client cannot detect a zone. */
        @Size(max = 64) String timezone
) {
}
