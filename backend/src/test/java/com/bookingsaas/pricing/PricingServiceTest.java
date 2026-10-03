package com.bookingsaas.pricing;

import com.bookingsaas.service.BookableService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("original price")
class PricingServiceTest {

    private static BookableService service(String price, boolean perParticipant) {
        BookableService service = new BookableService();
        service.setPrice(new BigDecimal(price));
        service.setPricePerParticipant(perParticipant);
        service.setMaxParticipants(8);
        return service;
    }

    @Test
    void a_flat_price_covers_the_whole_group() {
        assertThat(PricingService.originalPrice(service("80", false), 2)).isEqualByComparingTo("80.00");
    }

    @Test
    void a_per_participant_price_is_multiplied_by_the_group_size() {
        assertThat(PricingService.originalPrice(service("35", true), 4)).isEqualByComparingTo("140.00");
    }
}
