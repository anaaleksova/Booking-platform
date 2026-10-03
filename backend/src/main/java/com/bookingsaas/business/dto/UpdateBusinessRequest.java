package com.bookingsaas.business.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateBusinessRequest(
        @NotBlank @Size(max = 160) String name,

        /* Lower-case, dash-separated: it is the public URL segment. */
        @NotBlank @Size(max = 80)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "must contain only lower-case letters, digits and single dashes")
        String slug,

        @Size(max = 4000) String description,

        /* Booking page. */
        @Size(max = 200) String tagline,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a colour like #2f5bea") String accentColor,
        UUID logoMediaId,
        UUID coverMediaId,
        @Size(max = 500) @Pattern(regexp = "^$|^https?://.+", message = "must start with http:// or https://")
        String instagramUrl,
        @Size(max = 500) @Pattern(regexp = "^$|^https?://.+", message = "must start with http:// or https://")
        String facebookUrl,
        @Size(max = 500) @Pattern(regexp = "^$|^https?://.+", message = "must start with http:// or https://")
        String websiteUrl,
        Boolean showMap,
        Boolean showGallery,
        @Size(max = 40) String phone,
        @Email @Size(max = 320) String email,
        @Size(max = 500) String address,

        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter currency code")
        String currency,

        /* IANA zone id, validated against the JVM's zone database. */
        @NotBlank @Size(max = 64) String timezone
) {
}
