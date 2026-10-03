package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.AvailableSlot;
import com.bookingsaas.availability.dto.DayAvailability;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static com.bookingsaas.availability.AvailabilityTestFixtures.CHAIR;
import static com.bookingsaas.availability.AvailabilityTestFixtures.DATE;
import static com.bookingsaas.availability.AvailabilityTestFixtures.EMPLOYEE;
import static com.bookingsaas.availability.AvailabilityTestFixtures.ROOM;
import static com.bookingsaas.availability.AvailabilityTestFixtures.TABLE;
import static com.bookingsaas.availability.AvailabilityTestFixtures.at;
import static com.bookingsaas.availability.AvailabilityTestFixtures.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("availability engine")
class AvailabilityEngineTest {

    private final AvailabilityEngine engine = new AvailabilityEngine();

    private List<LocalTime> times(AvailabilityContext ctx) {
        DayAvailability day = engine.slotsForDay(ctx, DATE);
        return day.slots().stream().map(AvailableSlot::time).toList();
    }

    private static LocalTime t(String time) {
        return LocalTime.parse(time);
    }

    @Nested
    @DisplayName("single resource")
    class SingleResource {

        @Test
        void a_free_resource_makes_every_slot_in_opening_hours_available() {
            var ctx = context().room(1, 1).requires(100, ROOM, 1).build();

            assertThat(times(ctx)).first().isEqualTo(t("09:00"));
            // 60 minutes must end by 17:00, so the last start is 16:00.
            assertThat(times(ctx)).last().isEqualTo(t("16:00"));
            assertThat(times(ctx)).hasSize(15);
        }

        @Test
        void a_booked_resource_removes_the_overlapping_slots() {
            var ctx = context().room(1, 1).requires(100, ROOM, 1).busy(1, "10:00", "11:00").build();

            // 09:30 would run into 10:00 and 10:30 starts inside the booking;
            // 11:00 starts exactly when it ends, which is allowed.
            assertThat(times(ctx)).doesNotContain(t("09:30"), t("10:00"), t("10:30")).contains(t("09:00"), t("11:00"));
        }

        @Test
        void another_free_resource_of_the_same_type_keeps_the_slot_available() {
            var ctx = context().room(1, 1).room(2, 1).requires(100, ROOM, 1).busy(1, "10:00", "11:00").build();

            assertThat(times(ctx)).contains(t("10:00"));
            AvailableSlot ten = slotAt(ctx, "10:00");
            assertThat(ten.options().getFirst().candidateIds()).containsExactly(2L);
        }

        @Test
        void a_service_duration_decides_the_last_start_and_the_overlap() {
            var ctx = context().room(1, 1).requires(100, ROOM, 1).duration(90).busy(1, "12:00", "13:00").build();

            assertThat(times(ctx)).last().isEqualTo(t("15:30"));
            // 10:30 + 90 min = 12:00: touches the booking, still fine; 11:00 overlaps.
            assertThat(times(ctx)).contains(t("10:30")).doesNotContain(t("11:00"), t("11:30"));
        }

        @Test
        void buffers_are_part_of_the_blocked_window() {
            var ctx = context().room(1, 1).requires(100, ROOM, 1).buffers(0, 15).busy(1, "11:00", "12:00").build();

            // 10:00-11:00 plus 15 minutes of clean-up runs into 11:00.
            assertThat(times(ctx)).doesNotContain(t("10:00")).contains(t("09:30"));
        }
    }

    @Nested
    @DisplayName("several requirements")
    class SeveralRequirements {

        @Test
        void every_required_resource_type_must_be_free_at_the_same_time() {
            var ctx = context()
                    .room(1, 2).employee(11).employee(12)
                    .requires(100, ROOM, 1).requires(200, EMPLOYEE, 2)
                    .busy(12, "13:00", "14:00")
                    .build();

            // Room free all day, but only one of the two employees at 13:00.
            assertThat(times(ctx)).contains(t("12:00")).doesNotContain(t("12:30"), t("13:00"), t("13:30"));
        }

        @Test
        void a_couples_massage_gets_a_room_two_employees_and_two_chairs() {
            var ctx = context()
                    .room(1, 2).employee(11).employee(12).employee(13)
                    .resource(21, CHAIR, 1).resource(22, CHAIR, 1)
                    .requires(100, ROOM, 1).requires(200, EMPLOYEE, 2).requires(300, CHAIR, 2)
                    .participants(2)
                    .build();

            ResourceAllocation allocation = engine.allocate(ctx, at("10:00"), Map.of());

            assertThat(allocation.resources()).hasSize(5);
            assertThat(idsFor(allocation, 100)).containsExactly(1L);
            assertThat(idsFor(allocation, 200)).hasSize(2).doesNotHaveDuplicates();
            assertThat(idsFor(allocation, 300)).containsExactlyInAnyOrder(21L, 22L);
        }

        @Test
        void a_missing_required_resource_type_names_the_type() {
            var ctx = context().room(1, 1).requires(100, ROOM, 1).requires(200, EMPLOYEE, 1).build();

            SlotEvaluation evaluation = engine.evaluate(ctx, at("10:00"));

            assertThat(evaluation.available()).isFalse();
            assertThat(evaluation.reason()).isEqualTo(ErrorCode.RESOURCE_NOT_AVAILABLE);
            assertThat(evaluation.message()).contains("Employee");
        }

        @Test
        void an_optional_requirement_never_blocks_a_slot() {
            var ctx = context()
                    .room(1, 1).resource(21, CHAIR, 1)
                    .requires(100, ROOM, 1).requires(300, CHAIR, 1, false, false)
                    .busy(21, "10:00", "11:00")
                    .build();

            assertThat(times(ctx)).contains(t("10:00"));
            ResourceAllocation allocation = engine.allocate(ctx, at("10:00"), Map.of());
            assertThat(allocation.resourceIds()).containsExactly(1L);           // chair skipped, not failed
        }

        @Test
        void a_service_without_requirements_only_needs_the_business_to_be_open() {
            var ctx = context().build();

            assertThat(times(ctx)).hasSize(15);
        }
    }

    @Nested
    @DisplayName("participants and capacity")
    class Capacity {

        @Test
        void the_smallest_resource_that_fits_the_group_is_chosen() {
            var ctx = context()
                    .resource(1, TABLE, 6).resource(2, TABLE, 2).resource(3, TABLE, 4)
                    .requires(100, TABLE, 1).participants(3)
                    .build();

            ResourceAllocation allocation = engine.allocate(ctx, at("12:00"), Map.of());

            assertThat(allocation.resourceIds()).containsExactly(3L);           // 4 seats, not 6
        }

        @Test
        void a_group_larger_than_any_free_resource_has_no_availability() {
            var ctx = context()
                    .resource(1, TABLE, 2).resource(2, TABLE, 4)
                    .requires(100, TABLE, 1).participants(5)
                    .build();

            SlotEvaluation evaluation = engine.evaluate(ctx, at("12:00"));

            assertThat(evaluation.available()).isFalse();
            assertThat(evaluation.message()).contains("accommodate 5 participants");
        }

        @Test
        void when_the_big_table_is_taken_the_group_cannot_be_seated() {
            var ctx = context()
                    .resource(1, TABLE, 2).resource(2, TABLE, 6)
                    .requires(100, TABLE, 1).participants(5)
                    .busy(2, "12:00", "14:00")
                    .build();

            assertThat(times(ctx)).doesNotContain(t("12:00"), t("13:00")).contains(t("14:00"));
        }

        @Test
        void combined_capacity_of_the_required_quantity_counts() {
            var ctx = context().employee(11).employee(12).requires(200, EMPLOYEE, 2).participants(2).build();

            assertThat(engine.evaluate(ctx, at("10:00")).available()).isTrue();
        }

        @Test
        void unlimited_capacity_serves_any_group() {
            var ctx = context().room(1, null).requires(100, ROOM, 1).participants(40).build();

            assertThat(engine.evaluate(ctx, at("10:00")).available()).isTrue();
        }
    }

    @Nested
    @DisplayName("opening hours and schedules")
    class Hours {

        @Test
        void nothing_is_available_outside_opening_hours() {
            var ctx = context().room(1, 1).requires(100, ROOM, 1).build();

            SlotEvaluation evaluation = engine.evaluate(ctx, at("16:30"));     // would end 17:30

            assertThat(evaluation.available()).isFalse();
            assertThat(evaluation.reason()).isEqualTo(ErrorCode.OUTSIDE_BUSINESS_HOURS);
            assertThat(engine.evaluate(ctx, at("08:00")).reason()).isEqualTo(ErrorCode.OUTSIDE_BUSINESS_HOURS);
        }

        @Test
        void a_closed_day_has_no_slots() {
            var ctx = context().closedAllDay().room(1, 1).requires(100, ROOM, 1).build();

            assertThat(times(ctx)).isEmpty();
        }

        @Test
        void a_business_closure_removes_slots_but_keeps_the_grid() {
            var ctx = context().room(1, 1).requires(100, ROOM, 1).closure("12:00", "12:40").build();

            assertThat(times(ctx)).doesNotContain(t("11:30"), t("12:00"), t("12:30")).contains(t("11:00"), t("13:00"));
        }

        @Test
        void a_resource_is_only_offered_within_its_own_hours() {
            var ctx = context()
                    .employee(11).employee(12)
                    .requires(200, EMPLOYEE, 1)
                    .resourceHours(12, "09:00", "12:00")
                    .build();

            assertThat(slotAt(ctx, "10:00").options().getFirst().candidateIds()).contains(11L, 12L);
            assertThat(slotAt(ctx, "14:00").options().getFirst().candidateIds()).containsExactly(11L);
        }
    }

    @Nested
    @DisplayName("choosing resources")
    class Choosing {

        @Test
        void a_chosen_free_resource_is_used() {
            var ctx = context().employee(11).employee(12).requires(200, EMPLOYEE, 1, true, true).build();

            ResourceAllocation allocation = engine.allocate(ctx, at("10:00"), Map.of(200L, List.of(12L)));

            assertThat(allocation.resourceIds()).containsExactly(12L);
        }

        @Test
        void a_chosen_resource_that_is_already_booked_is_rejected() {
            var ctx = context().employee(11).employee(12).requires(200, EMPLOYEE, 1, true, true)
                    .busy(12, "10:00", "11:00").build();

            assertThatThrownBy(() -> engine.allocate(ctx, at("10:00"), Map.of(200L, List.of(12L))))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_AVAILABLE);
        }

        @Test
        void the_wrong_number_of_chosen_resources_is_rejected() {
            var ctx = context().employee(11).employee(12).requires(200, EMPLOYEE, 2).participants(2).build();

            assertThatThrownBy(() -> engine.allocate(ctx, at("10:00"), Map.of(200L, List.of(11L))))
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.INVALID_RESOURCE_SELECTION);
        }

        @Test
        void a_chosen_resource_too_small_for_the_group_is_rejected() {
            var ctx = context().resource(1, TABLE, 2).resource(2, TABLE, 6).requires(100, TABLE, 1)
                    .participants(4).build();

            assertThatThrownBy(() -> engine.allocate(ctx, at("12:00"), Map.of(100L, List.of(1L))))
                    .extracting(ex -> ((ApiException) ex).code())
                    .isEqualTo(ErrorCode.INVALID_RESOURCE_SELECTION);
        }

        @Test
        void automatic_assignment_prefers_the_least_busy_resource() {
            var ctx = context().employee(11).employee(12).requires(200, EMPLOYEE, 1)
                    .busy(11, "09:00", "10:00").busy(11, "14:00", "15:00").build();

            assertThat(engine.allocate(ctx, at("11:00"), Map.of()).resourceIds()).containsExactly(12L);
        }
    }

    @Nested
    @DisplayName("lead times")
    class LeadTimes {

        @Test
        void customers_cannot_book_in_the_past_or_too_soon() {
            var ctx = context().room(1, 1).requires(100, ROOM, 1)
                    .customerFacing(at("11:10"), 60, 30).build();

            assertThat(engine.evaluate(ctx, at("11:00")).reason()).isEqualTo(ErrorCode.RESERVATION_IN_THE_PAST);
            assertThat(engine.evaluate(ctx, at("12:00")).reason()).isEqualTo(ErrorCode.RESERVATION_TOO_SOON);
            assertThat(times(ctx)).first().isEqualTo(t("12:30"));
        }

        @Test
        void customers_cannot_book_beyond_the_advance_window() {
            var ctx = context().room(1, 1).requires(100, ROOM, 1)
                    .customerFacing(at(DATE.minusDays(10), "09:00"), 0, 5).build();

            assertThat(engine.evaluate(ctx, at("10:00")).reason()).isEqualTo(ErrorCode.RESERVATION_TOO_FAR_IN_ADVANCE);
        }
    }

    // --- helpers -----------------------------------------------------------

    private AvailableSlot slotAt(AvailabilityContext ctx, String time) {
        return engine.slotsForDay(ctx, DATE).slots().stream()
                .filter(slot -> slot.time().equals(t(time)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no slot at " + time));
    }

    private static List<Long> idsFor(ResourceAllocation allocation, long requirementId) {
        return allocation.resources().stream()
                .filter(item -> item.requirementId() == requirementId)
                .map(ResourceAllocation.AllocatedResource::resourceId)
                .toList();
    }
}
