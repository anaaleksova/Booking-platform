package com.bookingsaas.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Who may deliver a service is not set here — assignments are owned by the
 * staff and resource modules ({@code PUT /api/staff/{id}/services}), which
 * keeps the dependency between those modules one-directional.
 */
public record ServiceRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        @NotNull @Min(value = 1, message = "must be greater than 0") @Max(1440) Integer durationMinutes,
        @DecimalMin(value = "0.0", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal price,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter currency code") String currency,
        @NotNull @Min(0) @Max(480) Integer bufferBeforeMinutes,
        @NotNull @Min(0) @Max(480) Integer bufferAfterMinutes,
        @NotNull Boolean requiresStaff,
        @NotNull Boolean requiresResource,
        @NotNull Boolean active
) {
}
