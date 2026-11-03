package com.bookingsaas.onboarding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * Template prices are written in euros. A business in another currency gets
 * them converted roughly and rounded to a price a business would charge, so
 * a Skopje salon starts at 900 MKD rather than 15 MKD. They are placeholders:
 * the checklist's first step asks the owner to check them.
 */
final class SamplePrices {

    /** Rough euro rate and the step prices are rounded to. Anything else keeps the euro number. */
    private record Rate(int perEuro, int roundTo) {
    }

    private static final Map<String, Rate> RATES = Map.of(
            "MKD", new Rate(60, 50),
            "RSD", new Rate(115, 100),
            "TRY", new Rate(40, 50));

    private SamplePrices() {
    }

    static BigDecimal of(int euros, String currency) {
        Rate rate = currency == null ? null : RATES.get(currency.toUpperCase(Locale.ROOT));
        if (rate == null || euros == 0) {
            return BigDecimal.valueOf(euros);
        }
        long raw = (long) euros * rate.perEuro();
        long rounded = Math.max(rate.roundTo(),
                BigDecimal.valueOf(raw).divide(BigDecimal.valueOf(rate.roundTo()), 0, RoundingMode.HALF_UP)
                        .longValueExact() * rate.roundTo());
        return BigDecimal.valueOf(rounded);
    }
}
