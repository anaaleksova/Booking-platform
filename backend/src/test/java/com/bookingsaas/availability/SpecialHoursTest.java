package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.AvailableSlot;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static com.bookingsaas.availability.AvailabilityTestFixtures.DATE;
import static com.bookingsaas.availability.AvailabilityTestFixtures.EMPLOYEE;
import static com.bookingsaas.availability.AvailabilityTestFixtures.ROOM;
import static com.bookingsaas.availability.AvailabilityTestFixtures.at;
import static com.bookingsaas.availability.AvailabilityTestFixtures.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("special opening hours")
class SpecialHoursTest {

    private final AvailabilityEngine engine = new AvailabilityEngine();

    private List<LocalTime> times(AvailabilityContext ctx) {
        return engine.slotsForDay(ctx, DATE).slots().stream().map(AvailableSlot::time).toList();
    }

    @Test
    void shorter_hours_replace_the_week_on_that_date() {
        var ctx = context().room(1, 1).requires(100, ROOM, 1).specialHours("10:00", "14:00").build();

        assertThat(times(ctx)).first().isEqualTo(LocalTime.of(10, 0));
        assertThat(times(ctx)).last().isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void a_special_date_without_hours_is_closed() {
        var ctx = context().room(1, 1).requires(100, ROOM, 1).specialHours().build();

        assertThat(times(ctx)).isEmpty();
        assertThatThrownBy(() -> engine.allocate(ctx, at("10:00"), Map.of()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.OUTSIDE_BUSINESS_HOURS);
    }

    @Test
    void a_normally_closed_day_opens_when_it_has_special_hours() {
        var ctx = context().closedAllDay().room(1, 1).requires(100, ROOM, 1).specialHours("12:00", "15:00").build();

        assertThat(times(ctx)).containsExactly(LocalTime.of(12, 0), LocalTime.of(12, 30), LocalTime.of(13, 0),
                LocalTime.of(13, 30), LocalTime.of(14, 0));
    }

    @Test
    void on_an_extra_open_day_staff_without_shifts_that_weekday_follow_its_hours() {
        var ctx = context().closedAllDay().employee(2).worksOtherDaysOnly(2).requires(100, EMPLOYEE, 1)
                .specialHours("10:00", "12:00").build();

        assertThat(times(ctx)).contains(LocalTime.of(10, 0), LocalTime.of(11, 0));
    }

    @Test
    void on_a_shortened_working_day_staff_keep_their_shifts_within_the_new_hours() {
        var ctx = context().employee(2).resourceHours(2, "13:00", "17:00").requires(100, EMPLOYEE, 1)
                .specialHours("10:00", "15:00").build();

        assertThat(times(ctx)).containsExactly(LocalTime.of(13, 0), LocalTime.of(13, 30), LocalTime.of(14, 0));
    }

    @Test
    void staff_who_never_work_that_weekday_stay_off_on_an_ordinary_open_day_with_special_hours() {
        var ctx = context().employee(2).worksOtherDaysOnly(2).requires(100, EMPLOYEE, 1)
                .specialHours("10:00", "14:00").build();

        assertThat(times(ctx)).isEmpty();
    }
}
