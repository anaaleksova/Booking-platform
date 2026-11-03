package com.bookingsaas.onboarding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SamplePricesTest {

    @Test
    void euros_stay_as_they_are() {
        assertThat(SamplePrices.of(15, "EUR")).isEqualByComparingTo("15");
        assertThat(SamplePrices.of(15, "USD")).isEqualByComparingTo("15");
        assertThat(SamplePrices.of(15, null)).isEqualByComparingTo("15");
    }

    @Test
    void other_currencies_get_a_rounded_local_price() {
        assertThat(SamplePrices.of(15, "MKD")).isEqualByComparingTo("900");
        assertThat(SamplePrices.of(45, "mkd")).isEqualByComparingTo("2700");
        assertThat(SamplePrices.of(18, "RSD")).isEqualByComparingTo("2100");   // 2070 → 2100
        assertThat(SamplePrices.of(35, "TRY")).isEqualByComparingTo("1400");
    }

    @Test
    void free_stays_free() {
        assertThat(SamplePrices.of(0, "MKD")).isZero();
    }
}
