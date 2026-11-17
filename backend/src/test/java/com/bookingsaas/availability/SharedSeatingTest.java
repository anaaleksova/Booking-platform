package com.bookingsaas.availability;

import com.bookingsaas.availability.ResourceAllocation.AllocatedResource;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.bookingsaas.availability.AvailabilityTestFixtures.TABLE;
import static com.bookingsaas.availability.AvailabilityTestFixtures.at;
import static com.bookingsaas.availability.AvailabilityTestFixtures.context;
import static com.bookingsaas.availability.AvailabilityTestFixtures.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Shared tables that several groups can sit at, and tables kept for bigger groups. */
@DisplayName("shared seating")
class SharedSeatingTest {

    private final AvailabilityEngine engine = new AvailabilityEngine();

    @Test
    void a_small_group_at_a_shared_table_holds_only_its_seats() {
        var ctx = context().sharedTable(1, 8).requires(100, TABLE, 1).participants(2).build();

        List<AllocatedResource> held = engine.allocate(ctx, at("10:00"), Map.of()).resources();

        assertThat(held).containsExactly(new AllocatedResource(100L, 1L, 2));
    }

    @Test
    void the_seats_left_stay_bookable_by_other_groups() {
        var ctx = context().sharedTable(1, 8).requires(100, TABLE, 1).participants(6)
                .seats(1, "10:00", "11:00", 2).build();

        SlotEvaluation ten = engine.evaluate(ctx, at("10:00"));

        assertThat(ten.available()).isTrue();
        assertThat(ten.options().getFirst().seatsLeft()).containsEntry(1L, 6);
        assertThat(engine.allocate(ctx, at("10:00"), Map.of(100L, List.of(1L))).resources())
                .containsExactly(new AllocatedResource(100L, 1L, 6));
    }

    @Test
    void a_group_bigger_than_the_seats_left_does_not_fit() {
        var ctx = context().sharedTable(1, 8).requires(100, TABLE, 1).participants(7)
                .seats(1, "10:00", "11:00", 2).build();

        assertThat(engine.evaluate(ctx, at("10:00")).available()).isFalse();
        // Once that group has left, the whole table is free again.
        assertThat(engine.evaluate(ctx, at("11:00")).available()).isTrue();
    }

    @Test
    void seats_count_at_the_busiest_moment_of_the_booking() {
        // 2 seats 09:30–10:30 and 3 seats 10:30–11:30 never sit together: a
        // 10:00–11:00 booking meets at most 3 of them at once.
        var ctx = context().sharedTable(1, 8).requires(100, TABLE, 1).participants(5)
                .seats(1, "09:30", "10:30", 2).seats(1, "10:30", "11:30", 3).build();

        assertThat(engine.evaluate(ctx, at("10:00")).available()).isTrue();
        assertThat(AvailabilityEngine.peakSeats(ctx.seatsOf(1L), range("10:00", "11:00"))).isEqualTo(3);
    }

    @Test
    void with_no_preference_a_group_gets_a_table_of_its_own_before_a_shared_one() {
        var ctx = context().sharedTable(1, 4).resource(2, TABLE, 4).requires(100, TABLE, 1).participants(3).build();

        assertThat(engine.allocate(ctx, at("10:00"), Map.of()).resourceIds()).containsExactly(2L);
    }

    @Test
    void with_no_preference_the_shared_table_is_used_when_no_own_table_is_free() {
        var ctx = context().sharedTable(1, 4).resource(2, TABLE, 4).requires(100, TABLE, 1).participants(3)
                .busy(2, "10:00", "11:00").build();

        assertThat(engine.allocate(ctx, at("10:00"), Map.of()).resources())
                .containsExactly(new AllocatedResource(100L, 1L, 3));
    }

    @Test
    void with_no_preference_six_get_two_tables_of_their_own_before_the_shared_one() {
        // The restaurant floor: 2, 2, 4, 4 to themselves and a shared eight.
        var ctx = context().resource(1, TABLE, 2).resource(2, TABLE, 2).resource(3, TABLE, 4)
                .resource(4, TABLE, 4).sharedTable(5, 8)
                .requiresCombined(100, TABLE, 1, true).participants(6).build();

        List<Long> held = engine.allocate(ctx, at("10:00"), Map.of()).resourceIds();

        assertThat(held).hasSize(2).doesNotContain(5L);
        // The shared table can still be picked on purpose.
        assertThat(engine.allocate(ctx, at("10:00"), Map.of(100L, List.of(5L))).resourceIds()).containsExactly(5L);
    }

    @Test
    void a_private_booking_still_takes_the_whole_shared_table() {
        var ctx = context().sharedTable(1, 8).requires(100, TABLE, 1).participants(1)
                .busy(1, "10:00", "11:00").build();

        assertThat(engine.evaluate(ctx, at("10:00")).available()).isFalse();
    }

    @Test
    void a_table_kept_for_bigger_groups_is_not_given_to_a_small_one() {
        var small = context().bigGroupTable(1, 8, 5).requires(100, TABLE, 1).participants(2).build();
        var big = context().bigGroupTable(1, 8, 5).requires(100, TABLE, 1).participants(5).build();

        assertThat(engine.evaluate(small, at("10:00")).available()).isFalse();
        assertThat(engine.evaluate(big, at("10:00")).available()).isTrue();
        assertThat(engine.allocate(big, at("10:00"), Map.of()).resources())
                .containsExactly(new AllocatedResource(100L, 1L, null));
    }

    @Test
    void picking_a_table_kept_for_bigger_groups_says_why() {
        var ctx = context().bigGroupTable(1, 8, 5).sharedTable(2, 4).requires(100, TABLE, 1, true, true)
                .participants(2).build();

        assertThatThrownBy(() -> engine.allocate(ctx, at("10:00"), Map.of(100L, List.of(1L))))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_RESOURCE_SELECTION);
                    assertThat(ex.getMessage()).contains("groups of 5");
                });
    }
}
