package com.bookingsaas.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ServiceRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        @Size(max = 80) String category,
        UUID imageMediaId,
        @NotNull @Min(value = 5, message = "must be at least 5 minutes") @Max(1440) Integer durationMinutes,
        @NotNull @DecimalMin(value = "0.0", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal price,
        @NotNull Boolean pricePerParticipant,
        @NotNull @Min(value = 1, message = "must be at least 1") @Max(500) Integer maxParticipants,
        @NotNull @Min(0) @Max(480) Integer bufferBeforeMinutes,
        @NotNull @Min(0) @Max(480) Integer bufferAfterMinutes,
        @NotNull Boolean allowCustomerResourceSelection,
        @NotNull Boolean active,
        /* The complete list — replaces whatever the service required before. */
        @NotNull @Size(max = 20) List<@Valid @NotNull RequirementRequest> requirements
) {

    public record RequirementRequest(
            @NotNull Long resourceTypeId,
            @NotNull @Min(value = 1, message = "must be at least 1") @Max(50) Integer quantity,
            @NotNull Boolean required,
            @NotNull Boolean customerCanChoose,
            /* Combine resources for groups one cannot seat; absent means no. */
            Boolean combineResources
    ) {

        public boolean combines() {
            return Boolean.TRUE.equals(combineResources);
        }
    }
}
