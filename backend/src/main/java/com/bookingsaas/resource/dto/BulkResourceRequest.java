package com.bookingsaas.resource.dto;

import com.bookingsaas.common.domain.DayInterval;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Many alike resources at once: "Table 1" … "Table 20", four seats each.
 * Everything but the name is shared by all of them.
 *
 * @param hours own weekly schedule for each; null or empty = follow the opening hours
 */
public record BulkResourceRequest(
        @NotNull Long resourceTypeId,
        @NotBlank @Size(max = 150) String namePrefix,
        @NotNull @Min(0) @Max(9999) Integer firstNumber,
        @NotNull @Min(0) @Max(9999) Integer lastNumber,
        @Min(value = 1, message = "must be at least 1") @Max(10000) Integer capacity,
        Boolean sharedSeating,
        @Min(value = 1, message = "must be at least 1") @Max(10000) Integer minPartySize,
        @NotNull Boolean active,
        List<Long> serviceIds,
        List<DayInterval> hours
) {

    /** The most created in one go. */
    public static final int MAX = 100;
}
