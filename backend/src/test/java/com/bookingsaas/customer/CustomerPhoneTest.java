package com.bookingsaas.customer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A customer is recognised by phone in every channel only if the number is stored in one form. */
class CustomerPhoneTest {

    @Test
    void every_spelling_of_an_international_number_is_the_same_number() {
        assertThat(CustomerService.normalizePhone("+389 70 555 444")).isEqualTo("+38970555444");
        assertThat(CustomerService.normalizePhone("+389-70-555-444")).isEqualTo("+38970555444");
        assertThat(CustomerService.normalizePhone(" +38970555444 ")).isEqualTo("+38970555444");
    }

    @Test
    void a_number_without_a_country_code_is_kept_as_typed() {
        assertThat(CustomerService.normalizePhone("070 555 444")).isEqualTo("070 555 444");
    }

    @Test
    void blank_is_no_number() {
        assertThat(CustomerService.normalizePhone("  ")).isNull();
        assertThat(CustomerService.normalizePhone(null)).isNull();
    }
}
