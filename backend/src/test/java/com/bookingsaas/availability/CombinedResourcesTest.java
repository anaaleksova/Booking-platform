package com.bookingsaas.availability;

import com.bookingsaas.availability.ResourceAllocation.AllocatedResource;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.bookingsaas.availability.AvailabilityTestFixtures.ROOM;
import static com.bookingsaas.availability.AvailabilityTestFixtures.TABLE;
import static com.bookingsaas.availability.AvailabilityTestFixtures.at;
import static com.bookingsaas.availability.AvailabilityTestFixtures.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Combinable requirements in the engine: free resources are put together for
 * groups no single one can seat, only from what is free at that time, and the
 * reservation holds every resource of the chosen combination.
 */
@DisplayName("combined resources")
class CombinedResourcesTest {

    private static final long REQUIREMENT = 100L;

    private final AvailabilityEngine engine = new AvailabilityEngine();

    @Test
    void a_group_too_big_for_any_table_is_seated_at_several() {
        AvailabilityContext ctx = context()
                .resource(1, TABLE, 2).resource(2, TABLE, 2).resource(3, TABLE, 4).resource(4, TABLE, 4)
                .requiresCombined(REQUIREMENT, TABLE, 1, true)
                .participants(6)
                .build();

        SlotEvaluation evaluation = engine.evaluate(ctx, at("10:00"));

        assertThat(evaluation.available()).isTrue();
        assertThat(evaluation.options().get(0).allocations()).isNotEmpty()
                .allSatisfy(allocation -> assertThat(allocation.capacity()).isGreaterThanOrEqualTo(6));
        // One reservation, several resources: all of the best combination are held.
        List<Long> held = engine.allocate(ctx, at("10:00"), Map.of()).resourceIds();
        assertThat(held).hasSize(2);
        assertThat(held).containsAnyOf(3L, 4L);
    }

    @Test
    void without_combining_the_same_group_cannot_be_seated() {
        AvailabilityContext ctx = context()
                .resource(1, TABLE, 2).resource(2, TABLE, 4)
                .requires(REQUIREMENT, TABLE, 1)
                .participants(6)
                .build();

        assertThat(engine.evaluate(ctx, at("10:00")).available()).isFalse();
    }

    @Test
    void a_booked_resource_is_left_out_of_every_combination() {
        AvailabilityContext ctx = context()
                .resource(1, TABLE, 4).resource(2, TABLE, 2).resource(3, TABLE, 4)
                .busy(1, "09:30", "11:00")
                .requiresCombined(REQUIREMENT, TABLE, 1, true)
                .participants(6)
                .build();

        SlotEvaluation evaluation = engine.evaluate(ctx, at("10:00"));

        assertThat(evaluation.options().get(0).allocations())
                .isNotEmpty()
                .allSatisfy(allocation -> assertThat(allocation.resourceIds()).doesNotContain(1L));
    }

    @Test
    void a_resource_held_by_another_combination_is_not_offered_again() {
        // A + B are already booked together; B + C must not come back.
        AvailabilityContext ctx = context()
                .resource(1, TABLE, 2).resource(2, TABLE, 2).resource(3, TABLE, 2)
                .busy(1, "10:00", "11:00").busy(2, "10:00", "11:00")
                .requiresCombined(REQUIREMENT, TABLE, 1, true)
                .participants(4)
                .build();

        SlotEvaluation evaluation = engine.evaluate(ctx, at("10:00"));

        assertThat(evaluation.available()).isFalse();
        assertThat(evaluation.reason()).isEqualTo(ErrorCode.RESOURCE_NOT_AVAILABLE);
        assertThat(evaluation.message()).contains("alone or combined");
        assertThatThrownBy(() -> engine.allocate(ctx, at("10:00"), Map.of(REQUIREMENT, List.of(2L, 3L))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void a_chosen_combination_is_held_in_full() {
        AvailabilityContext ctx = context()
                .resource(1, TABLE, 2).resource(2, TABLE, 2).resource(3, TABLE, 4)
                .requiresCombined(REQUIREMENT, TABLE, 1, true)
                .participants(8)
                .build();

        List<AllocatedResource> held = engine.allocate(ctx, at("10:00"), Map.of(REQUIREMENT, List.of(1L, 2L, 3L)))
                .resources();

        assertThat(held).extracting(AllocatedResource::resourceId).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(held).allSatisfy(resource -> {
            assertThat(resource.requirementId()).isEqualTo(REQUIREMENT);
            assertThat(resource.seats()).isNull();         // private tables are held whole
        });
    }

    @Test
    void a_chosen_combination_must_seat_the_group_without_spare_tables() {
        AvailabilityContext ctx = context()
                .resource(1, TABLE, 2).resource(2, TABLE, 4).resource(3, TABLE, 4)
                .requiresCombined(REQUIREMENT, TABLE, 1, true)
                .participants(8)
                .build();

        assertThatThrownBy(() -> engine.allocate(ctx, at("10:00"), Map.of(REQUIREMENT, List.of(1L, 2L))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot accommodate 8");
        assertThatThrownBy(() -> engine.allocate(ctx, at("10:00"), Map.of(REQUIREMENT, List.of(1L, 2L, 3L))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("select fewer");
        assertThat(engine.allocate(ctx, at("10:00"), Map.of(REQUIREMENT, List.of(2L, 3L))).resourceIds())
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void tables_are_only_combined_within_one_area() {
        AvailabilityContext ctx = context()
                .tableInArea(1, 4, 7L).tableInArea(2, 4, 8L).tableInArea(3, 2, 8L).tableInArea(4, 4, 7L)
                .requiresCombined(REQUIREMENT, TABLE, 1, true)
                .participants(8)
                .build();

        // Only 1 + 4 (both in area 7) is offered.
        SlotEvaluation evaluation = engine.evaluate(ctx, at("10:00"));
        assertThat(evaluation.options().get(0).allocations())
                .extracting(AllocationFinder.Allocation::resourceIds)
                .containsExactly(List.of(1L, 4L));
        // 1 (area 7) + 2 (area 8) would seat eight too, but splits the group.
        assertThatThrownBy(() -> engine.allocate(ctx, at("10:00"), Map.of(REQUIREMENT, List.of(1L, 2L))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("same area");
    }

    @Test
    void a_single_room_is_preferred_to_two_when_assigning_automatically() {
        AvailabilityContext ctx = context()
                .room(1, 4).room(2, 6).room(3, 10)
                .requiresCombined(REQUIREMENT, ROOM, 1, false)
                .participants(8)
                .build();

        assertThat(engine.allocate(ctx, at("10:00"), Map.of()).resourceIds()).containsExactly(3L);
    }

    @Test
    void a_group_of_one_still_gets_the_smallest_table() {
        AvailabilityContext ctx = context()
                .resource(1, TABLE, 6).resource(2, TABLE, 2)
                .requiresCombined(REQUIREMENT, TABLE, 1, true)
                .participants(1)
                .build();

        assertThat(engine.allocate(ctx, at("10:00"), Map.of()).resourceIds()).containsExactly(2L);
    }

    @Test
    void a_shared_table_holds_only_the_seats_the_group_still_needs() {
        AvailabilityContext ctx = context()
                .resource(1, TABLE, 4)
                .sharedTable(2, 8)
                .seats(2, "10:00", "11:00", 6)
                .requiresCombined(REQUIREMENT, TABLE, 1, true)
                .participants(5)
                .build();

        List<AllocatedResource> held = engine.allocate(ctx, at("10:00"), Map.of(REQUIREMENT, List.of(1L, 2L)))
                .resources();

        assertThat(held).anySatisfy(resource -> {
            assertThat(resource.resourceId()).isEqualTo(2L);
            assertThat(resource.seats()).isEqualTo(1);     // four at the private table, one at the shared one
        });
    }
}
