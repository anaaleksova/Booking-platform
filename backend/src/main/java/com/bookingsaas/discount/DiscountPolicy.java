package com.bookingsaas.discount;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * The discount rules, as pure functions: whether a code may be used, and how
 * much it takes off.
 *
 * <p>No repositories and no clock, so every rule is testable in one line. The
 * usage limit is checked here for a helpful early answer, and enforced again
 * atomically by {@link DiscountRepository#redeem} when the reservation is
 * written.
 */
public final class DiscountPolicy {

    private DiscountPolicy() {
    }

    /**
     * @param originalPrice the price before any discount — what the minimum
     *                      booking value is compared against
     * @param today         the current date in the business timezone
     * @throws ApiException with a specific error code when the code cannot be used
     */
    public static void checkApplicable(Discount discount, Long serviceId, BigDecimal originalPrice, LocalDate today) {
        if (!discount.isActive()) {
            throw ApiException.of(ErrorCode.DISCOUNT_INACTIVE, "This discount code is not active.");
        }
        if (discount.getStartsOn() != null && today.isBefore(discount.getStartsOn())) {
            throw ApiException.of(ErrorCode.DISCOUNT_NOT_STARTED,
                    "This discount code is valid from " + discount.getStartsOn() + ".");
        }
        if (discount.getEndsOn() != null && today.isAfter(discount.getEndsOn())) {
            throw ApiException.of(ErrorCode.DISCOUNT_EXPIRED, "This discount code has expired.");
        }
        if (discount.getMaxUsage() != null && discount.getUsageCount() >= discount.getMaxUsage()) {
            throw ApiException.of(ErrorCode.DISCOUNT_USAGE_LIMIT_REACHED,
                    "This discount code has reached its usage limit.");
        }
        if (!discount.isAppliesToAllServices() && !discount.getServiceIds().contains(serviceId)) {
            throw ApiException.of(ErrorCode.DISCOUNT_NOT_APPLICABLE,
                    "This discount code cannot be used for this service.");
        }
        if (discount.getMinBookingValue() != null && originalPrice.compareTo(discount.getMinBookingValue()) < 0) {
            throw ApiException.of(ErrorCode.DISCOUNT_MINIMUM_NOT_MET,
                    "This discount code needs a booking value of at least "
                            + discount.getMinBookingValue().setScale(2, RoundingMode.HALF_UP) + ".");
        }
    }

    /**
     * The amount taken off, rounded to cents and never more than the price:
     * a fixed discount larger than the price makes it free, not negative.
     */
    public static BigDecimal amountFor(Discount discount, BigDecimal originalPrice) {
        BigDecimal amount = switch (discount.getType()) {
            case PERCENTAGE -> originalPrice.multiply(discount.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> discount.getValue().setScale(2, RoundingMode.HALF_UP);
        };
        return amount.min(originalPrice).max(BigDecimal.ZERO);
    }
}
