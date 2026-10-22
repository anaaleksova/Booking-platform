package com.bookingsaas.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A customer is recognised by phone in every channel only if a number is always stored the same way. */
class PhonesTest {

    @Test
    void every_spelling_of_a_number_is_the_same_number() {
        assertThat(Phones.normalize("+389 70 555 444", "MK")).isEqualTo("+38970555444");
        assertThat(Phones.normalize("+389-70-555-444", null)).isEqualTo("+38970555444");
        assertThat(Phones.normalize(" +38970555444 ", null)).isEqualTo("+38970555444");
    }

    @Test
    void a_local_number_is_read_in_the_business_country() {
        assertThat(Phones.normalize("070 555 444", "MK")).isEqualTo("+38970555444");
        assertThat(Phones.normalize("070 555 444", "RS")).isEqualTo("+38170555444");
    }

    @Test
    void a_local_number_with_no_country_to_read_it_in_is_kept_as_typed() {
        assertThat(Phones.normalize("070 555 444", null)).isEqualTo("070 555 444");
    }

    @Test
    void the_business_country_comes_from_its_own_number() {
        assertThat(Phones.regionOf("+389 2 312 4567")).isEqualTo("MK");
        assertThat(Phones.regionOf("02 312 4567")).isNull();
        assertThat(Phones.regionOf(null)).isNull();
    }

    @Test
    void blank_is_no_number() {
        assertThat(Phones.normalize("  ", "MK")).isNull();
    }
}
