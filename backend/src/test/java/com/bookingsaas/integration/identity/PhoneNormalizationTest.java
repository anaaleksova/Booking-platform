package com.bookingsaas.integration.identity;

import com.bookingsaas.common.error.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("phone numbers typed in chats")
class PhoneNormalizationTest {

    @Test
    @DisplayName("the business's own number tells which country it is in")
    void regionOfBusinessPhone() {
        assertThat(ExternalIdentityService.regionOf("+389 2 312 4567")).isEqualTo("MK");
        assertThat(ExternalIdentityService.regionOf("+44 20 7946 0958")).isEqualTo("GB");
        assertThat(ExternalIdentityService.regionOf("02 312 4567")).isNull();
        assertThat(ExternalIdentityService.regionOf(null)).isNull();
    }

    @Test
    @DisplayName("a local number is read in the business's country")
    void localNumberWithDefaultRegion() {
        assertThat(ExternalIdentityService.normalizePhone("072 345 678", "MK")).isEqualTo("+38972345678");
        assertThat(ExternalIdentityService.normalizePhone("072345678", "MK")).isEqualTo("+38972345678");
    }

    @Test
    @DisplayName("an international number wins over the default country")
    void internationalNumberIgnoresDefault() {
        assertThat(ExternalIdentityService.normalizePhone("+44 7911 123456", "MK")).isEqualTo("+447911123456");
        assertThat(ExternalIdentityService.normalizePhone("0044 7911 123456", "MK")).isEqualTo("+447911123456");
    }

    @Test
    @DisplayName("without a country, a local number still needs a country code")
    void localNumberWithoutRegion() {
        assertThatThrownBy(() -> ExternalIdentityService.normalizePhone("072 345 678", null))
                .isInstanceOf(ApiException.class);
    }
}
