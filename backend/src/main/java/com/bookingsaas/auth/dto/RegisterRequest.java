package com.bookingsaas.auth.dto;

import com.bookingsaas.business.BusinessType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
        @Size(max = 64) String timezone,
        /* What kind of business it is; picks the starting setup. Optional; absent means OTHER. */
        BusinessType businessType,
        /* ISO-4217. Optional; absent means EUR. */
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter currency code") String currency
) {
}
