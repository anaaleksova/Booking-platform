package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.AvailableSlot;
import com.bookingsaas.availability.dto.DayAvailability;
import com.bookingsaas.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static com.bookingsaas.availability.AvailabilityTestFixtures.DATE;
import static com.bookingsaas.availability.AvailabilityTestFixtures.at;
import static com.bookingsaas.availability.AvailabilityTestFixtures.context;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine's behaviour, one rule at a time.
 *
 * <p>Ordered roughly as the algorithm runs: opening hours, then the slot grid,
 * then bookings and buffers, then staff, then resources, then the lead-time
 * policy.
 */
class AvailabilityEngineTest {

    private final AvailabilityEngine engine = new AvailabilityEngine();

    private List<LocalTime> slotsOf(AvailabilityContext context) {
        return engine.slotsForDay(context, DATE).slots().stream().map(AvailableSlot::time).toList();
    }

    @Nested
    @DisplayName("working hours")
    class WorkingHours {

        @Test
        void generates_slots_across_the_open_window() {
            List<LocalTime> slots = slotsOf(context().businessHours("09:00", "17:00").service(60).build());

            assertThat(slots).containsExactly(
                    LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0),
                    LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0));
        }

        @Test
        void returns_nothing_on_a_closed_day() {
            assertThat(slotsOf(context().closed().build())).isEmpty();
        }

        @Test
        void appointment_must_finish_before_closing() {
            // 16:30 would run to 17:30 — past closing, so it is not offered.
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "17:00").service(60).slotInterval(30).build());

            assertThat(slots).endsWith(LocalTime.of(16, 0));
        }

        @Test
        void two_intervals_a_day_create_a_lunch_break() {
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "13:00")
                    .addBusinessHours("14:00", "18:00")
                    .service(60)
                    .build());

            assertThat(slots).containsExactly(
                    LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0),
                    LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0), LocalTime.of(17, 0));
        }
    }

    @Nested
    @DisplayName("slot interval")
    class SlotInterval {

        @Test
        void a_45_minute_service_on_a_15_minute_grid_starts_every_quarter_hour() {
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "11:00").service(45).slotInterval(15).build());

            assertThat(slots).containsExactly(
                    LocalTime.of(9, 0), LocalTime.of(9, 15), LocalTime.of(9, 30), LocalTime.of(9, 45),
                    LocalTime.of(10, 0), LocalTime.of(10, 15));
        }

        @Test
        void a_business_wide_block_does_not_shift_the_grid() {
            // The grid stays on the hour; only the slots the meeting actually
            // covers disappear.
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "14:00")
                    .businessBlock("11:20", "12:10")
                    .service(60)
                    .build());

            assertThat(slots).containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(13, 0));
        }
    }

    @Nested
    @DisplayName("existing bookings")
    class ExistingBookings {

        @Test
        void a_taken_hour_is_removed_and_the_rest_stays() {
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "13:00").service(60)
                    .staffBusy(1L, "10:00", "11:00")
                    .build());

            assertThat(slots).containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 0), LocalTime.of(12, 0));
        }

        @Test
        void a_partially_overlapping_booking_still_blocks_the_slot() {
            // 10:30-11:00 collides with a 10:00 start of a 60 minute service.
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "12:00").service(60)
                    .staffBusy(1L, "10:30", "11:00")
                    .build());

            assertThat(slots).containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 0));
        }

        @Test
        void back_to_back_appointments_are_allowed() {
            // Half-open intervals: a booking ending at 10:00 leaves 10:00 free.
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "11:00").service(60)
                    .staffBusy(1L, "09:00", "10:00")
                    .build());

            assertThat(slots).containsExactly(LocalTime.of(10, 0));
        }
    }

    @Nested
    @DisplayName("buffers")
    class Buffers {

        @Test
        void a_buffer_after_pushes_the_next_start_later() {
            // Existing 10:00-11:00 booking plus a 15 minute after-buffer means
            // the next 60 minute appointment cannot start before 11:15.
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "13:00").service(60).buffers(0, 15).slotInterval(15)
                    .staffBusy(1L, "10:00", "11:15")
                    .build());

            assertThat(slots).contains(LocalTime.of(11, 15))
                    .doesNotContain(LocalTime.of(11, 0), LocalTime.of(10, 45));
        }

        @Test
        void a_buffer_before_protects_the_preparation_time() {
            // A 10 minute before-buffer means a 12:00 start reserves from 11:50,
            // which collides with a booking ending at 12:00... only if that
            // booking's own window reaches into it.
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "14:00").service(60).buffers(10, 0).slotInterval(30)
                    .staffBusy(1L, "11:00", "12:00")
                    .build());

            assertThat(slots).doesNotContain(LocalTime.of(12, 0))
                    .contains(LocalTime.of(12, 30));
        }

        @Test
        void buffers_may_extend_past_closing_time() {
            // The appointment must fit inside opening hours; cleaning up after
            // the last customer does not have to.
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "10:00").service(60).buffers(0, 30).build());

            assertThat(slots).containsExactly(LocalTime.of(9, 0));
        }
    }

    @Nested
    @DisplayName("staff")
    class StaffRules {

        @Test
        void staff_hours_narrow_the_business_hours() {
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "17:00").service(60)
                    .staff(1L).staffHours(1L, "10:00", "13:00")
                    .build());

            assertThat(slots).containsExactly(LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0));
        }

        @Test
        void staff_hours_cannot_widen_the_business_hours() {
            // Ana starts at 08:00, but the studio opens at 09:00.
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "12:00").service(60)
                    .staff(1L).staffHours(1L, "08:00", "18:00")
                    .build());

            assertThat(slots).containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));
        }

        @Test
        void staff_without_their_own_schedule_follow_the_business() {
            assertThat(slotsOf(context().businessHours("09:00", "11:00").service(60).staff(1L).build()))
                    .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0));
        }

        @Test
        void staff_scheduled_only_on_other_days_are_unavailable() {
            assertThat(slotsOf(context().service(60).staff(1L).staffOffToday(1L).build())).isEmpty();
        }

        @Test
        void any_available_keeps_a_slot_while_at_least_one_person_is_free() {
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "11:00").service(60)
                    .staff(1L, 2L)
                    .staffBusy(1L, "09:00", "10:00")
                    .build());

            assertThat(slots).containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0));
        }

        @Test
        void a_slot_reports_only_the_staff_who_are_actually_free() {
            DayAvailability day = engine.slotsForDay(context()
                    .businessHours("09:00", "10:00").service(60)
                    .staff(1L, 2L)
                    .staffBusy(1L, "09:00", "10:00")
                    .build(), DATE);

            assertThat(day.slots()).singleElement()
                    .extracting(AvailableSlot::availableStaffIds)
                    .isEqualTo(List.of(2L));
        }

        @Test
        void no_slots_remain_when_every_staff_member_is_taken() {
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "10:00").service(60)
                    .staff(1L, 2L)
                    .staffBusy(1L, "09:00", "10:00")
                    .staffBusy(2L, "08:00", "12:00")
                    .build());

            assertThat(slots).isEmpty();
        }

        @Test
        void blocked_time_removes_availability_like_a_booking() {
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "12:00").service(60)
                    .staffBusy(1L, "10:00", "11:00")   // vacation, meeting, lunch — same thing to the engine
                    .build());

            assertThat(slots).containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 0));
        }
    }

    @Nested
    @DisplayName("resources")
    class ResourceRules {

        @Test
        void a_service_needing_a_resource_is_unavailable_without_one() {
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "11:00").service(60).requires(true, true)
                    .resources()
                    .build());

            assertThat(slots).isEmpty();
        }

        @Test
        void a_slot_survives_while_one_of_several_resources_is_free() {
            DayAvailability day = engine.slotsForDay(context()
                    .businessHours("09:00", "10:00").service(60).requires(true, true)
                    .resources(10L, 11L)
                    .resourceBusy(10L, "09:00", "10:00")
                    .build(), DATE);

            assertThat(day.slots()).singleElement()
                    .extracting(AvailableSlot::availableResourceIds)
                    .isEqualTo(List.of(11L));
        }

        @Test
        void all_resources_busy_removes_the_slot() {
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "10:00").service(60).requires(true, true)
                    .resources(10L, 11L)
                    .resourceBusy(10L, "09:00", "10:00")
                    .resourceBusy(11L, "09:00", "10:00")
                    .build());

            assertThat(slots).isEmpty();
        }

        @Test
        void a_room_rental_needs_no_staff_at_all() {
            // Service = room, staff = none, resource = required: the third shape
            // the generic engine has to handle.
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "11:00").service(60).requires(false, true)
                    .staff()
                    .resources(10L)
                    .build());

            assertThat(slots).containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0));
        }
    }

    @Nested
    @DisplayName("booking rules")
    class LeadTimes {

        @Test
        void minimum_notice_hides_slots_that_are_too_soon() {
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "13:00").service(60)
                    .leadTimes(120, 60)
                    .now(at("09:30"))
                    .build());

            // 09:30 + 2h = 11:30, so the first bookable start is 12:00.
            assertThat(slots).containsExactly(LocalTime.of(12, 0));
        }

        @Test
        void maximum_advance_hides_days_beyond_the_window() {
            AvailabilityContext context = context()
                    .businessHours("09:00", "11:00").service(60)
                    .leadTimes(0, 7)
                    .now(at(DATE.minusDays(30), "09:00"))
                    .build();

            assertThat(engine.slotsForDay(context, DATE).slots()).isEmpty();
        }

        @Test
        void the_business_may_book_inside_its_own_notice_period() {
            // enforceLeadTimes is false for dashboard bookings.
            List<LocalTime> slots = slotsOf(context()
                    .businessHours("09:00", "11:00").service(60)
                    .now(at("09:00"))
                    .build());

            assertThat(slots).containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0));
        }
    }

    @Nested
    @DisplayName("evaluating one exact time")
    class ExactTime {

        @Test
        void accepts_a_time_that_is_not_on_the_slot_grid() {
            // Manual bookings are typed in, not picked: 10:07 is legitimate.
            SlotEvaluation evaluation = engine.evaluate(
                    context().businessHours("09:00", "17:00").service(60).build(), at("10:07"));

            assertThat(evaluation.available()).isTrue();
            assertThat(evaluation.staffIds()).containsExactly(1L);
        }

        @Test
        void reports_why_a_time_outside_opening_hours_fails() {
            SlotEvaluation evaluation = engine.evaluate(
                    context().businessHours("09:00", "17:00").service(60).build(), at("18:00"));

            assertThat(evaluation.available()).isFalse();
            assertThat(evaluation.reason()).isEqualTo(ErrorCode.BOOKING_OUTSIDE_WORKING_HOURS);
        }

        @Test
        void reports_a_conflict_as_no_staff_available() {
            SlotEvaluation evaluation = engine.evaluate(
                    context().businessHours("09:00", "17:00").service(60)
                            .staffBusy(1L, "10:00", "11:00").build(),
                    at("10:30"));

            assertThat(evaluation.available()).isFalse();
            assertThat(evaluation.reason()).isEqualTo(ErrorCode.NO_STAFF_AVAILABLE);
        }

        @Test
        void reports_a_time_that_breaks_the_notice_rule() {
            SlotEvaluation evaluation = engine.evaluate(
                    context().businessHours("09:00", "17:00").service(60)
                            .leadTimes(120, 60).now(at("09:30")).build(),
                    at("10:00"));

            assertThat(evaluation.reason()).isEqualTo(ErrorCode.BOOKING_TOO_SOON);
        }

        @Test
        void reports_a_time_in_the_past() {
            SlotEvaluation evaluation = engine.evaluate(
                    context().businessHours("09:00", "17:00").service(60)
                            .leadTimes(0, 60).now(at("12:00")).build(),
                    at("10:00"));

            assertThat(evaluation.reason()).isEqualTo(ErrorCode.BOOKING_IN_THE_PAST);
        }
    }

    @Nested
    @DisplayName("multi-day queries")
    class MultiDay {

        @Test
        void returns_one_entry_per_day_including_closed_ones() {
            AvailabilityContext context = context().businessHours("09:00", "11:00").service(60).build();

            List<DayAvailability> days = engine.availableSlots(context, DATE, DATE.plusDays(2));

            assertThat(days).hasSize(3);
            assertThat(days.getFirst().hasAvailability()).isTrue();
            // Only the test date's weekday has working hours configured.
            assertThat(days.get(1).hasAvailability()).isFalse();
        }
    }
}
