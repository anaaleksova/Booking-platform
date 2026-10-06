package com.bookingsaas.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sign-up for someone who wants to book, not run a business: an account with
 * no business membership. One account books with any business on the platform.
 */
public record CustomerRegisterRequest(
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 128, message = "must be at least 8 characters") String password
) {
}
