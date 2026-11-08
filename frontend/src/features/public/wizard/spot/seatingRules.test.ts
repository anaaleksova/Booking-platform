import { describe, expect, it } from 'vitest';
import type { ChoosableOption, ChoosableRequirement, ResourceOption, Slot } from '@/types/api';
import { captionOf, seatingRules, withoutTakenPicks } from './seatingRules';

const option = (id: number, capacity: number | null, extra: Partial<ChoosableOption> = {}): ChoosableOption => ({
  id,
  name: `T${id}`,
  capacity,
  shared: false,
  ...extra,
});

const requirement = (extra: Partial<ChoosableRequirement> = {}): ChoosableRequirement => ({
  requirementId: 1,
  resourceTypeName: 'Table',
  quantity: 1,
  combinable: false,
  resources: [],
  floorPlans: [],
  ...extra,
});

const slot = (free: ResourceOption[], allocations: { ids: number[]; capacity: number }[] = []) =>
  ({
    requirements: [
      {
        requirementId: 1,
        resources: free,
        allocations: allocations.map((allocation) => ({
          resources: allocation.ids.map((id) => ({ id, name: `T${id}`, capacity: null })),
          capacity: allocation.capacity,
          area: null,
        })),
      },
    ],
  }) as unknown as Slot;

const free = (...ids: number[]) => ids.map((id) => ({ id, name: `T${id}`, capacity: null }));

describe('seatingRules — one table per group', () => {
  const rules = seatingRules(requirement(), slot([...free(1, 2), { id: 3, name: 'T3', capacity: 8, seatsLeft: 2 }]), 4);

  it('a table that is too small is never offered, free or not', () => {
    expect(rules.statusOf(option(1, 2))).toBe('small');
  });

  it('a table kept for bigger groups', () => {
    expect(rules.statusOf(option(2, 8, { minPartySize: 6 }))).toBe('bigGroups');
  });

  it('a shared table counts the seats still free', () => {
    const shared = option(3, 8, { shared: true });
    expect(rules.seatsLeftOf(shared)).toBe(2);
    expect(rules.statusOf(shared)).toBe('small');
    expect(captionOf(shared, 'small', 2)).toBe('2 of 8 free');
  });

  it('a table missing from the free list is taken', () => {
    expect(rules.statusOf(option(9, 4))).toBe('taken');
  });

  it('a click picks one table, a second click unpicks it', () => {
    expect(rules.afterClick(option(2, 4), [])).toEqual([2]);
    expect(rules.afterClick(option(2, 4), [2])).toEqual([]);
    expect(rules.afterClick(option(1, 2), [])).toBeNull();
  });
});

describe('seatingRules — "choose 2"', () => {
  const rules = seatingRules(requirement({ quantity: 2 }), slot(free(1, 2, 3)), 2);

  it('small ones count: two together may seat the group', () => {
    expect(rules.statusOf(option(1, 1))).toBe('free');
  });

  it('a third pick replaces the oldest', () => {
    expect(rules.afterClick(option(1, 1), [])).toEqual([1]);
    expect(rules.afterClick(option(2, 1), [1])).toEqual([1, 2]);
    expect(rules.afterClick(option(3, 1), [1, 2])).toEqual([2, 3]);
  });
});

describe('seatingRules — tables combined for larger groups', () => {
  const rules = seatingRules(requirement({ combinable: true }), slot(free(1, 2, 3), [{ ids: [1, 2], capacity: 6 }]), 6);

  it('a small table is offered when the server can combine it', () => {
    expect(rules.statusOf(option(1, 2))).toBe('free');
    expect(rules.statusOf(option(3, 2))).toBe('small');
  });

  it('clicking it picks the whole combination', () => {
    expect(rules.afterClick(option(1, 2), [])).toEqual([1, 2]);
    expect(rules.isSuggestionPicked(rules.suggestions[0], [2, 1])).toBe(true);
  });
});

describe('seatingRules — the best fit is recommended', () => {
  it('a couple is recommended the two-seat tables, not the bigger ones', () => {
    const rules = seatingRules(
      requirement({ combinable: true }),
      slot(free(1, 2, 3, 4), [
        { ids: [1], capacity: 2 },
        { ids: [2], capacity: 2 },
        { ids: [3], capacity: 4 },
        { ids: [4], capacity: 8 },
      ]),
      2,
    );
    expect(rules.recommended.map((allocation) => allocation.resources[0].id)).toEqual([1, 2]);
    // The bigger tables are still free to pick.
    expect(rules.statusOf(option(3, 4))).toBe('free');
  });

  it('a big table alone beats a combination', () => {
    const rules = seatingRules(
      requirement({ combinable: true }),
      slot(free(1, 2, 3), [
        { ids: [3], capacity: 8 },
        { ids: [1, 2], capacity: 8 },
      ]),
      8,
    );
    expect(rules.recommended).toHaveLength(1);
  });
});

describe('withoutTakenPicks', () => {
  it('keeps what is still free and reports no change', () => {
    expect(withoutTakenPicks([requirement()], slot(free(1)), { 1: [1] })).toBeNull();
  });

  it('drops a taken resource', () => {
    expect(withoutTakenPicks([requirement({ quantity: 2 })], slot(free(1)), { 1: [1, 2] })).toEqual({ 1: [1] });
  });

  it('drops a whole combination when part of it is gone', () => {
    expect(withoutTakenPicks([requirement({ combinable: true })], slot(free(1)), { 1: [1, 2] })).toEqual({});
  });
});
