package com.bookingsaas.pricing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuoteRequest(
        @NotNull Long serviceId,
        @NotNull @Min(1) Integer participants,
        @Size(max = 40) String discountCode
) {
}
