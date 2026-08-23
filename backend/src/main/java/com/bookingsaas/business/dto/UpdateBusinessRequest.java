package com.bookingsaas.business.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBusinessRequest(
        @NotBlank @Size(max = 160) String name,

        /* Lower-case, dash-separated: it is the public URL segment. */
        @NotBlank @Size(max = 80)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "must contain only lower-case letters, digits and single dashes")
        String slug,

        @Size(max = 4000) String description,
        @Size(max = 1024) String logoUrl,
        @Size(max = 40) String phone,
        @Email @Size(max = 320) String email,
        @Size(max = 500) String address,

        /* IANA zone id, validated against the JVM's zone database. */
        @NotBlank @Size(max = 64) String timezone
) {
}
