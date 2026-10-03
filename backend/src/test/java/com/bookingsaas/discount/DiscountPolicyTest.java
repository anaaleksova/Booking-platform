package com.bookingsaas.discount;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("discount rules")
class DiscountPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final Long SERVICE = 7L;

    private static Discount percentage(String value) {
        Discount discount = new Discount();
        discount.setCode("SUMMER20");
        discount.setType(DiscountType.PERCENTAGE);
        discount.setValue(new BigDecimal(value));
        discount.setActive(true);
        return discount;
    }

    private static Discount fixed(String value) {
        Discount discount = percentage(value);
        discount.setType(DiscountType.FIXED_AMOUNT);
        return discount;
    }

    private static ErrorCode rejection(Discount discount, String price) {
        try {
            DiscountPolicy.checkApplicable(discount, SERVICE, new BigDecimal(price), TODAY);
            return null;
        } catch (ApiException ex) {
            return ex.code();
        }
    }

    @Test
    void a_valid_discount_is_accepted() {
        Discount discount = percentage("20");
        discount.setStartsOn(LocalDate.of(2026, 6, 1));
        discount.setEndsOn(LocalDate.of(2026, 6, 30));

        assertThatCode(() -> DiscountPolicy.checkApplicable(discount, SERVICE, new BigDecimal("50"), TODAY))
                .doesNotThrowAnyException();
    }

    @Test
    void the_validity_period_is_inclusive_on_both_ends() {
        Discount discount = percentage("20");
        discount.setStartsOn(TODAY);
        discount.setEndsOn(TODAY);

        assertThat(rejection(discount, "50")).isNull();
    }

    @Test
    void an_expired_discount_is_rejected() {
        Discount discount = percentage("20");
        discount.setEndsOn(TODAY.minusDays(1));

        assertThat(rejection(discount, "50")).isEqualTo(ErrorCode.DISCOUNT_EXPIRED);
    }

    @Test
    void a_discount_that_has_not_started_is_rejected() {
        Discount discount = percentage("20");
        discount.setStartsOn(TODAY.plusDays(1));

        assertThat(rejection(discount, "50")).isEqualTo(ErrorCode.DISCOUNT_NOT_STARTED);
    }

    @Test
    void an_inactive_discount_is_rejected() {
        Discount discount = percentage("20");
        discount.setActive(false);

        assertThat(rejection(discount, "50")).isEqualTo(ErrorCode.DISCOUNT_INACTIVE);
    }

    @Test
    void the_minimum_booking_value_must_be_met() {
        Discount discount = percentage("20");
        discount.setMinBookingValue(new BigDecimal("60"));

        assertThat(rejection(discount, "59.99")).isEqualTo(ErrorCode.DISCOUNT_MINIMUM_NOT_MET);
        assertThat(rejection(discount, "60.00")).isNull();
    }

    @Test
    void a_used_up_discount_is_rejected() {
        Discount discount = percentage("20");
        discount.setMaxUsage(1);
        setUsage(discount, 1);

        assertThat(rejection(discount, "50")).isEqualTo(ErrorCode.DISCOUNT_USAGE_LIMIT_REACHED);
    }

    @Test
    void a_discount_limited_to_other_services_is_rejected() {
        Discount discount = percentage("20");
        discount.setAppliesToAllServices(false);
        discount.setServiceIds(Set.of(99L));

        assertThat(rejection(discount, "50")).isEqualTo(ErrorCode.DISCOUNT_NOT_APPLICABLE);

        discount.setServiceIds(Set.of(99L, SERVICE));
        assertThat(rejection(discount, "50")).isNull();
    }

    @Test
    void a_percentage_is_taken_off_and_rounded_to_cents() {
        assertThat(DiscountPolicy.amountFor(percentage("20"), new BigDecimal("80.00"))).isEqualByComparingTo("16.00");
        assertThat(DiscountPolicy.amountFor(percentage("15"), new BigDecimal("33.33"))).isEqualByComparingTo("5.00");
    }

    @Test
    void a_fixed_amount_is_capped_at_the_price() {
        assertThat(DiscountPolicy.amountFor(fixed("15"), new BigDecimal("80"))).isEqualByComparingTo("15.00");
        assertThat(DiscountPolicy.amountFor(fixed("100"), new BigDecimal("35"))).isEqualByComparingTo("35");
    }

    @Test
    void checks_stop_at_the_first_failed_rule_with_a_specific_code() {
        Discount discount = percentage("20");
        discount.setActive(false);
        discount.setEndsOn(TODAY.minusDays(1));

        assertThatThrownBy(() -> DiscountPolicy.checkApplicable(discount, SERVICE, BigDecimal.TEN, TODAY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not active");
    }

    /** usage_count is read-only on the entity (only guarded UPDATEs change it), so tests set it reflectively. */
    private static void setUsage(Discount discount, int usage) {
        try {
            var field = Discount.class.getDeclaredField("usageCount");
            field.setAccessible(true);
            field.setInt(discount, usage);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
