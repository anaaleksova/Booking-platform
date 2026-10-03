package com.bookingsaas.pricing.dto;

import com.bookingsaas.pricing.PriceQuote;

import java.math.BigDecimal;

/** The price summary shown before confirming. Recomputed on submit; never trusted back from the client. */
public record QuoteResponse(
        BigDecimal originalPrice,
        BigDecimal discountAmount,
        BigDecimal finalPrice,
        String currency,
        String discountCode,
        String discountName
) {

    public static QuoteResponse from(PriceQuote quote) {
        return new QuoteResponse(quote.originalPrice(), quote.discountAmount(), quote.finalPrice(),
                quote.currency(), quote.discountCode(), quote.discountName());
    }
}
