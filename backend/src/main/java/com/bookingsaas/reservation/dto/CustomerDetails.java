package com.bookingsaas.reservation.dto;

import com.bookingsaas.common.validation.ValidPhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Contact details entered while booking. Phone is required so the business can
 * always reach the customer. The email is ignored: the signed-in account's
 * email is used instead, so nobody can book in someone else's name. A returning
 * customer is recognised by email or phone rather than duplicated.
 */
public record CustomerDetails(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Email @Size(max = 320) String email,
        /* International form, e.g. +38970123456; checked against the country's numbering plan. */
        @NotBlank @ValidPhone @Size(max = 40) String phone
) {
}
