package com.bookingsaas.pricing;

import com.bookingsaas.business.Business;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.discount.Discount;
import com.bookingsaas.discount.DiscountPolicy;
import com.bookingsaas.discount.DiscountService;
import com.bookingsaas.service.BookableService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The single place a reservation's price is computed. */
@Service
public class PricingService {

    private final DiscountService discountService;

    public PricingService(DiscountService discountService) {
        this.discountService = discountService;
    }

    /**
     * @param discountCode optional; an unknown or unusable code fails with the
     *                     specific reason rather than being silently ignored
     */
    @Transactional(readOnly = true)
    public PriceQuote quote(Business business, BookableService service, int participants, String discountCode) {
        if (participants < 1 || participants > service.getMaxParticipants()) {
            throw ApiException.of(ErrorCode.INVALID_PARTICIPANTS,
                    "Choose between 1 and " + service.getMaxParticipants() + " participants.");
        }
        BigDecimal original = originalPrice(service, participants);
        if (discountCode == null || discountCode.isBlank()) {
            return new PriceQuote(original, zero(), original, business.getCurrency(), null, null, null);
        }
        Discount discount = discountService.requireApplicable(business.getId(), discountCode, service.getId(), original);
        BigDecimal amount = DiscountPolicy.amountFor(discount, original);
        return new PriceQuote(original, amount, original.subtract(amount), business.getCurrency(),
                discount.getId(), discount.getCode(), discount.getName());
    }

    /** Flat per reservation, or per participant — as the service is configured. */
    public static BigDecimal originalPrice(BookableService service, int participants) {
        BigDecimal unit = service.getPrice() == null ? BigDecimal.ZERO : service.getPrice();
        BigDecimal total = service.isPricePerParticipant() ? unit.multiply(BigDecimal.valueOf(participants)) : unit;
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
