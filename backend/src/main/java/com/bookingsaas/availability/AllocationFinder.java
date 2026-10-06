package com.bookingsaas.availability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Finds sets of free resources that together seat a group — "Table 3 + Table 4"
 * for a party of eight — without anyone having configured that combination.
 *
 * <p>Pure computation, like the engine: give it the resources that are free at
 * one time and it returns the best few ways to cover the group, ranked by
 * {@link #RANKING}.
 *
 * <h2>Why this does not explode</h2>
 *
 * Trying every subset of N resources is 2<sup>N</sup>. The search is bounded in
 * three ways instead:
 *
 * <ol>
 *   <li><b>Interchangeable resources are counted, not enumerated.</b> Within an
 *       area, resources of equal capacity are grouped into one class, and the
 *       search decides <em>how many</em> of each class to use. Forty two-seat
 *       tables are one class, not forty branches; a desk floor of identical
 *       desks is a single class. The concrete resources are chosen afterwards,
 *       most preferred first.</li>
 *   <li><b>Pruning.</b> Classes are visited largest first, so a branch stops as
 *       soon as the remaining capacity cannot reach the group, as soon as it
 *       already seats the group (adding more would only waste resources), and
 *       as soon as it already wastes more seats than the worst option kept.</li>
 *   <li><b>Hard caps.</b> At most {@link #MAX_RESOURCES} resources per
 *       allocation and {@link #MAX_STEPS} search steps per call, whatever the
 *       data. The caps only ever cost alternatives, never correctness: every
 *       option returned is valid.</li>
 * </ol>
 *
 * <h2>Areas</h2>
 *
 * A combination is only formed from resources in the same area (a floor plan),
 * so a group is never split between the dining room and the terrace. Resources
 * on no plan form one area of their own. A single resource is always fine.
 */
public final class AllocationFinder {

    /** The most resources one allocation may combine. */
    public static final int MAX_RESOURCES = 20;
    /** How many options are returned at most. */
    public static final int DEFAULT_LIMIT = 5;
    /** Search steps per call; a safety net far above what real floors need. */
    static final int MAX_STEPS = 20_000;

    /**
     * The order in which allocations are preferred — change it here and only
     * here:
     *
     * <ol>
     *   <li>fewer resources: one big enough room beats two small ones;</li>
     *   <li>less spare capacity: 4 + 2 for six beats 4 + 4;</li>
     *   <li>the preference order of the resources used (smallest, least busy
     *       first, as the engine sorts them).</li>
     * </ol>
     */
    static final Comparator<Allocation> RANKING = Comparator
            .comparingInt((Allocation allocation) -> allocation.resources().size())
            .thenComparingLong(Allocation::excess)
            .thenComparingInt(Allocation::preferenceScore);

    private AllocationFinder() {
    }

    /**
     * One way to seat the group.
     *
     * @param resources       the resources to hold, most preferred first
     * @param capacity        their combined (effective) capacity
     * @param excess          capacity beyond the group
     * @param areaId          the area all of them stand in; {@code null} for
     *                        resources on no plan
     * @param preferenceScore sum of the resources' positions in the preference
     *                        order — lower is better
     */
    public record Allocation(List<ResourceSpec> resources, long capacity, long excess, Long areaId,
                             int preferenceScore) {

        public List<Long> resourceIds() {
            return resources.stream().map(ResourceSpec::id).toList();
        }
    }

    /**
     * @param preferred    free resources in preference order (as the engine
     *                     orders them); a shared one carries the seats it has
     *                     left as its capacity
     * @param minResources the fewest resources an allocation may use (the
     *                     requirement's quantity)
     * @param participants the group to seat
     * @param limit        how many options to return at most
     * @return the best allocations, best first; empty when the group cannot be
     *         seated
     */
    public static List<Allocation> find(List<ResourceSpec> preferred, int minResources, int participants, int limit) {
        if (limit < 1 || preferred.size() < minResources) {
            return List.of();
        }
        Map<Long, Integer> rank = new LinkedHashMap<>();
        preferred.forEach(resource -> rank.putIfAbsent(resource.id(), rank.size()));

        Map<Long, List<ResourceSpec>> byArea = new LinkedHashMap<>();
        preferred.forEach(resource -> byArea.computeIfAbsent(resource.areaId(), key -> new ArrayList<>()).add(resource));

        // A bounded max-heap on RANKING: the root is the worst allocation kept.
        PriorityQueue<Allocation> best = new PriorityQueue<>(RANKING.reversed());
        int[] steps = {0};

        // Searched area by area: a combination never spans two.
        for (List<ResourceSpec> area : byArea.values()) {
            new AreaSearch(area, minResources, participants, limit, rank, best, steps).run();
        }

        List<Allocation> result = new ArrayList<>(best);
        result.sort(RANKING);
        return List.copyOf(result);
    }

    /** Whether any allocation exists, without collecting alternatives. */
    public static boolean canSeat(List<ResourceSpec> preferred, int minResources, int participants) {
        return !find(preferred, minResources, participants, 1).isEmpty();
    }

    /**
     * Whether a hand-picked set is a sensible allocation: at least
     * {@code minResources}, all in one area, seating the group, and with no
     * resource that could be left out — so nobody holds a spare table "just in
     * case".
     *
     * @return {@code null} when valid, otherwise why not
     */
    public static String rejectionOf(List<ResourceSpec> picked, int minResources, int participants) {
        if (picked.size() < minResources) {
            return "Select at least " + minResources + ".";
        }
        if (picked.size() > Math.max(MAX_RESOURCES, minResources)) {
            return "Select at most " + Math.max(MAX_RESOURCES, minResources) + ".";
        }
        if (picked.size() > 1 && picked.stream().map(ResourceSpec::areaId).distinct().count() > 1) {
            return "Combined resources must be in the same area.";
        }
        long capacity = picked.stream().mapToLong(ResourceSpec::effectiveCapacity).sum();
        if (capacity < participants) {
            return "Together they cannot accommodate " + participants + " participants.";
        }
        long smallest = picked.stream().mapToLong(ResourceSpec::effectiveCapacity).min().orElse(0);
        if (picked.size() > minResources && capacity - smallest >= participants) {
            return "The group fits without all of them — select fewer.";
        }
        return null;
    }

    // --- search ------------------------------------------------------------

    /** Interchangeable resources: same capacity, same area, in preference order. */
    private record CapacityClass(long capacity, List<ResourceSpec> members) {
    }

    /**
     * Depth-first over capacity classes, largest first, choosing a count for
     * each. Counts per class are what make identical resources cheap.
     */
    private static final class AreaSearch {

        private final List<CapacityClass> classes;
        /** suffixCapacity[i]: everything from class i on, the most still addable. */
        private final long[] suffixCapacity;
        private final int[] suffixCount;
        private final int minResources;
        private final int participants;
        private final int limit;
        private final Map<Long, Integer> rank;
        private final PriorityQueue<Allocation> best;
        private final int[] steps;
        private final int[] counts;

        AreaSearch(List<ResourceSpec> area, int minResources, int participants, int limit,
                   Map<Long, Integer> rank, PriorityQueue<Allocation> best, int[] steps) {
            Map<Long, List<ResourceSpec>> byCapacity = new LinkedHashMap<>();
            area.stream()
                    .filter(resource -> resource.effectiveCapacity() > 0)
                    .forEach(resource -> byCapacity.computeIfAbsent(resource.effectiveCapacity(),
                            key -> new ArrayList<>()).add(resource));
            this.classes = byCapacity.entrySet().stream()
                    .map(entry -> new CapacityClass(entry.getKey(), entry.getValue()))
                    .sorted(Comparator.comparingLong(CapacityClass::capacity).reversed())
                    .toList();
            int n = classes.size();
            this.suffixCapacity = new long[n + 1];
            this.suffixCount = new int[n + 1];
            for (int i = n - 1; i >= 0; i--) {
                CapacityClass cls = classes.get(i);
                int usable = Math.min(cls.members().size(), MAX_RESOURCES);
                suffixCapacity[i] = saturatedAdd(suffixCapacity[i + 1], cls.capacity() * usable);
                suffixCount[i] = suffixCount[i + 1] + cls.members().size();
            }
            this.minResources = minResources;
            this.participants = participants;
            this.limit = limit;
            this.rank = rank;
            this.best = best;
            this.steps = steps;
            this.counts = new int[n];
        }

        void run() {
            int maxResources = Math.max(MAX_RESOURCES, minResources);
            if (suffixCount[0] >= minResources && suffixCapacity[0] >= participants) {
                search(0, 0, 0, maxResources);
            }
        }

        private void search(int index, long capacity, int used, int maxResources) {
            if (++steps[0] > MAX_STEPS) {
                return;
            }
            if (capacity >= participants && used >= minResources) {
                // Seats the group; anything more would be a resource it does not need.
                offer(best, limit, materialize());
                return;
            }
            if (index == classes.size() || used == maxResources
                    || saturatedAdd(capacity, suffixCapacity[index]) < participants
                    || used + suffixCount[index] < minResources) {
                return;
            }
            // Already wasting more than every kept option, with more still to add: no better here.
            if (best.size() == limit && used + 1 > best.peek().resources().size()) {
                return;
            }

            CapacityClass cls = classes.get(index);
            int most = Math.min(cls.members().size(), maxResources - used);
            // More of the big class first: that reaches the group with fewer resources.
            for (int take = most; take >= 0; take--) {
                // The last one added is the smallest so far; if the group fits
                // without it (and the minimum is met), it would be a spare.
                if (take > 0 && used + take - 1 >= minResources
                        && capacity + cls.capacity() * (take - 1) >= participants) {
                    continue;
                }
                counts[index] = take;
                search(index + 1, capacity + cls.capacity() * take, used + take, maxResources);
                if (steps[0] > MAX_STEPS) {
                    break;
                }
            }
            counts[index] = 0;
        }

        private Allocation materialize() {
            List<ResourceSpec> chosen = new ArrayList<>();
            for (int i = 0; i < classes.size(); i++) {
                chosen.addAll(classes.get(i).members().subList(0, counts[i]));
            }
            chosen.sort(Comparator.comparingInt(resource -> rank.get(resource.id())));
            return allocationOf(chosen, participants, rank);
        }
    }

    private static Allocation allocationOf(List<ResourceSpec> resources, int participants,
                                           Map<Long, Integer> rank) {
        long capacity = resources.stream().mapToLong(ResourceSpec::effectiveCapacity)
                .reduce(0, AllocationFinder::saturatedAdd);
        int score = resources.stream().mapToInt(resource -> rank.get(resource.id())).sum();
        return new Allocation(List.copyOf(resources), capacity, capacity - participants,
                resources.get(0).areaId(), score);
    }

    /** Keeps the {@code limit} best, dropping duplicates. */
    private static void offer(PriorityQueue<Allocation> best, int limit, Allocation candidate) {
        for (Allocation kept : best) {
            if (Objects.equals(kept.resourceIds(), candidate.resourceIds())) {
                return;
            }
        }
        if (best.size() < limit) {
            best.add(candidate);
        } else if (RANKING.compare(candidate, best.peek()) < 0) {
            best.poll();
            best.add(candidate);
        }
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }
}
