package com.bookingsaas.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "^\\s*\\d{6}\\s*$", message = "must be the 6-digit code from the email") String code,
        @NotBlank @Size(min = 8, max = 128, message = "must be at least 8 characters") String newPassword
) {
}
