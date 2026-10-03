package com.bookingsaas.pricing;

import java.math.BigDecimal;

/**
 * What a reservation costs, computed by the server.
 *
 * <p>The customer's summary screen shows this, and the reservation endpoint
 * computes it again from scratch — a price or discount sent by a client is
 * never trusted.
 *
 * @param originalPrice  service price, times participants for per-person pricing
 * @param discountAmount amount taken off by the code, never more than the price
 * @param finalPrice     {@code originalPrice - discountAmount}
 */
public record PriceQuote(
        BigDecimal originalPrice,
        BigDecimal discountAmount,
        BigDecimal finalPrice,
        String currency,
        Long discountId,
        String discountCode,
        String discountName
) {

    public boolean hasDiscount() {
        return discountId != null;
    }
}
