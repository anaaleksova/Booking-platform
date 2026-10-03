package com.bookingsaas.discount.dto;

import com.bookingsaas.discount.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DiscountRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 40)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "may contain only letters, digits, '-' and '_'")
        String code,
        @NotNull DiscountType type,
        @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") @Digits(integer = 10, fraction = 2)
        BigDecimal value,
        LocalDate startsOn,
        LocalDate endsOn,
        @NotNull Boolean active,
        @Min(value = 1, message = "must be at least 1") Integer maxUsage,
        @DecimalMin(value = "0.0", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal minBookingValue,
        @NotNull Boolean appliesToAllServices,
        List<Long> serviceIds
) {
}
