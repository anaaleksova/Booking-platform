package com.bookingsaas.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * What a customer fills in on the public page. No account, no password — a
 * name and one way to reach them is enough.
 *
 * <p>There is no resource field on purpose: which reformer or room is used is
 * the business's concern, and the engine assigns one.
 */
public record PublicBookingRequest(
        @NotNull Long serviceId,
        /** Null means "any available staff member". */
        Long staffId,
        @NotNull LocalDateTime startDateTime,
        @NotBlank @Size(max = 160) String name,
        @Pattern(regexp = "^$|^\\+?[0-9 ()./-]{6,40}$", message = "is not a valid phone number")
        @Size(max = 40) String phone,
        @Email @Size(max = 320) String email,
        @Size(max = 2000) String notes
) {
}
