package com.bookingsaas.discount;

public enum DiscountType {
    /** {@code value} is a percentage of the original price, 0 < value ≤ 100. */
    PERCENTAGE,
    /** {@code value} is an amount in the business currency, capped at the original price. */
    FIXED_AMOUNT
}
