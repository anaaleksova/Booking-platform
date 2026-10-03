package com.bookingsaas.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record EmployeeRequest(
        @NotBlank @Size(max = 160) String name,
        @Email @Size(max = 320) String email,
        @Pattern(regexp = "^$|^\\+?[0-9 ()./-]{6,40}$", message = "is not a valid phone number")
        @Size(max = 40) String phone,
        /* An uploaded image shown when customers choose who serves them. */
        UUID imageMediaId,
        @NotNull Boolean active,
        /* Participants served at once. 1 for one-to-one work; higher for a
           group class instructor; null = not limited by participants. */
        @Min(value = 1, message = "must be at least 1") @Max(10000) Integer capacity,
        /* Services this person performs. Empty = every service that needs an
           employee; null on update = leave unchanged. */
        List<Long> serviceIds
) {
}
