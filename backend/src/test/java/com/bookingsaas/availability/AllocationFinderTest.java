package com.bookingsaas.availability;

import com.bookingsaas.availability.AllocationFinder.Allocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * The search for resource combinations, on its own: which sets seat a group,
 * in what order they are preferred, and that the search stays bounded.
 *
 * <p>Resources are passed in the engine's preference order (ascending
 * capacity), as they are in production.
 */
@DisplayName("allocation finder")
class AllocationFinderTest {

    private static final long TYPE = 1L;

    @Nested
    @DisplayName("what is returned")
    class Returned {

        @Test
        void a_single_resource_that_fits_is_returned() {
            List<Allocation> found = find(4, table(1, 4));

            assertThat(ids(found)).containsExactly(List.of(1L));
            assertThat(found.get(0).capacity()).isEqualTo(4);
        }

        @Test
        void several_resources_are_combined_when_none_fits_alone() {
            List<Allocation> found = find(6, table(1, 2), table(2, 2), table(3, 2));

            assertThat(ids(found)).containsExactly(List.of(1L, 2L, 3L));
        }

        @Test
        void a_combination_uses_no_more_resources_than_the_group_needs() {
            // 2 + 2 + 4 would seat six, but so does 2 + 4 — the extra table is never offered.
            List<Allocation> found = find(6, table(1, 2), table(2, 2), table(3, 4));

            assertThat(found).isNotEmpty().allSatisfy(allocation -> {
                assertThat(allocation.resources()).hasSize(2);
                assertThat(allocation.capacity()).isEqualTo(6);
            });
            assertThat(ids(found).get(0)).containsExactlyInAnyOrder(1L, 3L);
        }

        @Test
        void an_exact_fit_is_preferred_over_spare_capacity() {
            // A:4 B:2 C:4 D:4 for six → A + B (6) before C + D (8).
            List<Allocation> found = find(6, table(2, 2), table(1, 4), table(3, 4), table(4, 4));

            assertThat(ids(found).get(0)).containsExactlyInAnyOrder(1L, 2L);
            assertThat(found.get(0).excess()).isZero();
            assertThat(found).extracting(Allocation::excess).isSorted();
        }

        @Test
        void one_resource_is_preferred_over_a_combination() {
            // A:6 B:4 C:2 for six → A, although B + C is also exact.
            List<Allocation> found = find(6, table(3, 2), table(2, 4), table(1, 6));

            assertThat(ids(found).get(0)).containsExactly(1L);
            assertThat(ids(found)).anySatisfy(pair -> assertThat(pair).containsExactlyInAnyOrder(2L, 3L));
        }

        @Test
        void a_big_room_beats_two_small_ones_even_with_spare_seats() {
            // Room A:4 B:6 C:10 for eight → C alone, not A + B.
            List<Allocation> found = find(8, table(1, 4), table(2, 6), table(3, 10));

            assertThat(ids(found).get(0)).containsExactly(3L);
        }

        @Test
        void nothing_is_returned_when_everything_together_is_too_small() {
            assertThat(find(10, table(1, 4), table(2, 2))).isEmpty();
            assertThat(AllocationFinder.canSeat(List.of(table(1, 4), table(2, 2)), 1, 10)).isFalse();
        }

        @Test
        void the_minimum_number_of_resources_is_respected() {
            // At least two, even though the six-seat table alone would do.
            List<Allocation> found = AllocationFinder.find(List.of(table(1, 2), table(2, 6)), 2, 4, 5);

            assertThat(ids(found)).containsExactly(List.of(1L, 2L));
        }

        @Test
        void the_restaurant_example_returns_both_exact_pairs() {
            // T1:2 T2:2 T3:4 T4:4 T5:6 for eight → T5 + T1 (or T2) and T3 + T4.
            List<Allocation> found = find(8, table(1, 2), table(2, 2), table(3, 4), table(4, 4), table(5, 6));

            List<List<Long>> exactPairs = found.stream()
                    .filter(allocation -> allocation.resources().size() == 2 && allocation.excess() == 0)
                    .map(Allocation::resourceIds)
                    .toList();
            assertThat(exactPairs).anySatisfy(pair -> assertThat(pair).contains(5L).containsAnyOf(1L, 2L));
            assertThat(exactPairs).anySatisfy(pair -> assertThat(pair).containsExactlyInAnyOrder(3L, 4L));
        }

        @Test
        void desks_of_one_seat_are_combined_for_a_team() {
            List<Allocation> found = find(4, desk(1), desk(2), desk(3), desk(4), desk(5));

            assertThat(found).isNotEmpty();
            assertThat(found.get(0).resources()).hasSize(4);
            // Interchangeable desks give one option, the most preferred desks first.
            assertThat(ids(found).get(0)).containsExactly(1L, 2L, 3L, 4L);
        }

        @Test
        void a_shared_resource_counts_only_the_seats_it_has_left() {
            ResourceSpec halfFull = new ResourceSpec(1L, TYPE, "Long table", 8, true, null).withCapacity(3);

            assertThat(find(5, halfFull)).isEmpty();
            assertThat(ids(find(5, halfFull, table(2, 2)))).containsExactly(List.of(1L, 2L));
        }

        @Test
        void no_more_than_the_limit_is_returned() {
            List<ResourceSpec> tables = new ArrayList<>();
            for (long id = 1; id <= 12; id++) {
                tables.add(table(id, (int) (id % 4) + 2));
            }
            assertThat(AllocationFinder.find(tables, 1, 9, 3)).hasSize(3);
        }
    }

    @Nested
    @DisplayName("areas")
    class Areas {

        @Test
        void a_combination_never_spans_two_areas() {
            List<Allocation> found = find(6,
                    inArea(table(1, 4), 100L), inArea(table(2, 2), 200L), inArea(table(3, 4), 200L));

            // 1 (area 100) + 2 (area 200) would be exact, but splits the group.
            assertThat(ids(found)).containsExactly(List.of(2L, 3L));
            assertThat(found.get(0).areaId()).isEqualTo(200L);
        }

        @Test
        void a_single_resource_may_come_from_any_area() {
            List<Allocation> found = find(4, inArea(table(1, 4), 100L), inArea(table(2, 4), 200L));

            assertThat(ids(found)).containsExactlyInAnyOrder(List.of(1L), List.of(2L));
        }

        @Test
        void a_hand_picked_set_across_areas_is_rejected() {
            String rejection = AllocationFinder.rejectionOf(
                    List.of(inArea(table(1, 4), 100L), inArea(table(2, 4), 200L)), 1, 8);

            assertThat(rejection).contains("same area");
        }
    }

    @Nested
    @DisplayName("hand-picked combinations")
    class HandPicked {

        @Test
        void a_set_that_seats_the_group_is_accepted() {
            assertThat(AllocationFinder.rejectionOf(List.of(table(1, 4), table(2, 4)), 1, 8)).isNull();
        }

        @Test
        void a_set_too_small_is_rejected() {
            assertThat(AllocationFinder.rejectionOf(List.of(table(1, 4), table(2, 2)), 1, 8))
                    .contains("cannot accommodate 8");
        }

        @Test
        void a_set_with_a_spare_resource_is_rejected() {
            assertThat(AllocationFinder.rejectionOf(List.of(table(1, 4), table(2, 4), table(3, 2)), 1, 8))
                    .contains("select fewer");
        }
    }

    @Nested
    @DisplayName("bounded search")
    class Bounded {

        @Test
        void hundreds_of_identical_desks_are_searched_instantly() {
            List<ResourceSpec> desks = LongStream.rangeClosed(1, 500).mapToObj(AllocationFinderTest::desk).toList();

            List<Allocation> found = assertTimeoutPreemptively(Duration.ofSeconds(1),
                    () -> AllocationFinder.find(desks, 1, 15, 5));

            assertThat(found).hasSize(1);
            assertThat(found.get(0).resources()).hasSize(15);
        }

        @Test
        void many_tables_of_mixed_sizes_stay_within_bounds() {
            List<ResourceSpec> tables = new ArrayList<>();
            for (long id = 1; id <= 300; id++) {
                tables.add(table(id, (int) (id * 7 % 11) + 1));
            }
            tables.sort((a, b) -> Long.compare(a.effectiveCapacity(), b.effectiveCapacity()));

            List<Allocation> found = assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> AllocationFinder.find(tables, 1, 37, 5));

            assertThat(found).hasSize(5).allSatisfy(allocation -> {
                assertThat(allocation.capacity()).isGreaterThanOrEqualTo(37);
                assertThat(allocation.resources()).hasSizeLessThanOrEqualTo(AllocationFinder.MAX_RESOURCES);
            });
            assertThat(found).isSortedAccordingTo(AllocationFinder.RANKING);
        }

        @Test
        void a_group_needing_more_than_the_cap_is_not_seated() {
            List<ResourceSpec> desks = LongStream.rangeClosed(1, 50).mapToObj(AllocationFinderTest::desk).toList();

            assertThat(AllocationFinder.find(desks, 1, AllocationFinder.MAX_RESOURCES + 1, 5)).isEmpty();
        }
    }

    // --- helpers -----------------------------------------------------------

    private static List<Allocation> find(int participants, ResourceSpec... preferred) {
        return AllocationFinder.find(List.of(preferred), 1, participants, AllocationFinder.DEFAULT_LIMIT);
    }

    private static List<List<Long>> ids(List<Allocation> allocations) {
        return allocations.stream().map(Allocation::resourceIds).toList();
    }

    private static ResourceSpec table(long id, int capacity) {
        return new ResourceSpec(id, TYPE, "Table " + id, capacity);
    }

    private static ResourceSpec desk(long id) {
        return new ResourceSpec(id, TYPE, "Desk " + id, 1);
    }

    private static ResourceSpec inArea(ResourceSpec resource, long areaId) {
        return new ResourceSpec(resource.id(), resource.resourceTypeId(), resource.name(), resource.capacity(),
                resource.shared(), resource.minPartySize(), areaId);
    }
}
