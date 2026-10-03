package com.bookingsaas.resource.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ResourceRequest(
        @NotNull Long resourceTypeId,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        /* Participants served at once; null = not limited by participants. */
        @Min(value = 1, message = "must be at least 1") @Max(10000) Integer capacity,
        @NotNull Boolean active,
        /* Several bookings share it until its seats are taken; needs a capacity. Null = false. */
        Boolean sharedSeating,
        /* Smallest group given this resource when it is not shared; null = any group. */
        @Min(value = 1, message = "must be at least 1") @Max(10000) Integer minPartySize,
        UUID imageMediaId,
        /* Services this resource may serve. Empty = every service needing its
           type; null on update = leave unchanged. */
        List<Long> serviceIds
) {
}
