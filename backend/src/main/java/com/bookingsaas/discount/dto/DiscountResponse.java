package com.bookingsaas.discount.dto;

import com.bookingsaas.discount.Discount;
import com.bookingsaas.discount.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DiscountResponse(
        Long id,
        String name,
        String code,
        DiscountType type,
        BigDecimal value,
        LocalDate startsOn,
        LocalDate endsOn,
        boolean active,
        Integer maxUsage,
        int usageCount,
        BigDecimal minBookingValue,
        boolean appliesToAllServices,
        List<Long> serviceIds,
        /* ACTIVE, SCHEDULED, EXPIRED, EXHAUSTED or INACTIVE — derived for display. */
        String state
) {

    public static DiscountResponse from(Discount discount, LocalDate today) {
        return new DiscountResponse(discount.getId(), discount.getName(), discount.getCode(), discount.getType(),
                discount.getValue(), discount.getStartsOn(), discount.getEndsOn(), discount.isActive(),
                discount.getMaxUsage(), discount.getUsageCount(), discount.getMinBookingValue(),
                discount.isAppliesToAllServices(), discount.getServiceIds().stream().sorted().toList(),
                stateOf(discount, today));
    }

    private static String stateOf(Discount discount, LocalDate today) {
        if (!discount.isActive()) {
            return "INACTIVE";
        }
        if (discount.getEndsOn() != null && today.isAfter(discount.getEndsOn())) {
            return "EXPIRED";
        }
        if (discount.getMaxUsage() != null && discount.getUsageCount() >= discount.getMaxUsage()) {
            return "EXHAUSTED";
        }
        if (discount.getStartsOn() != null && today.isBefore(discount.getStartsOn())) {
            return "SCHEDULED";
        }
        return "ACTIVE";
    }
}
