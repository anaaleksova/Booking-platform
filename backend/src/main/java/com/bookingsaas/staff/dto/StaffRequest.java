package com.bookingsaas.staff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StaffRequest(
        @NotBlank @Size(max = 160) String name,
        @Email @Size(max = 320) String email,
        @Pattern(regexp = "^$|^\\+?[0-9 ()./-]{6,40}$", message = "is not a valid phone number")
        @Size(max = 40) String phone,
        @Size(max = 1024) String photoUrl,
        @NotNull Boolean active,

        /* Services this person can deliver. Empty means "everything the
           business offers", so staff are bookable before any assignment. */
        List<Long> serviceIds
) {
}
