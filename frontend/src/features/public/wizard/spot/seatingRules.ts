import type { AllocationOption, ChoosableOption, ChoosableRequirement, Slot } from '@/types/api';

/** 'small': too small (or too few seats left) for the group; 'bigGroups': kept for larger groups. */
export type SpotStatus = 'free' | 'taken' | 'small' | 'bigGroups';

/** Free resources at this time → seats left (shared resources) or null (private ones). */
export function freeResources(slot: Slot, requirementId: number): Map<number, number | null> {
  const requirement = slot.requirements.find((candidate) => candidate.requirementId === requirementId);
  return new Map((requirement?.resources ?? []).map((resource) => [resource.id, resource.seatsLeft ?? null]));
}

/**
 * What the customer may pick for one requirement at one time, for a group of
 * this size. The server has the final word; this decides what is offered.
 */
export function seatingRules(requirement: ChoosableRequirement, slot: Slot, people: number) {
  const combined = requirement.combinable;
  const many = requirement.quantity > 1 && !combined;
  const free = freeResources(slot, requirement.requirementId);
  /** The server's ways to seat the group at this time, best first (combinable requirements only). */
  const suggestions: AllocationOption[] =
    slot.requirements.find((candidate) => candidate.requirementId === requirement.requirementId)?.allocations ?? [];
  const suggestionWith = (id: number) =>
    suggestions.find((allocation) => allocation.resources.some((resource) => resource.id === id));

  /** Seats still free on a shared table at this time; null for private ones. */
  const seatsLeftOf = (option: ChoosableOption): number | null =>
    option.shared ? (free.has(option.id) ? (free.get(option.id) ?? option.capacity) : 0) : null;

  const statusOf = (option: ChoosableOption): SpotStatus => {
    if (combined) {
      // A small table is still useful when the server can combine it with others.
      if (!option.shared && option.minPartySize && people < option.minPartySize) {
        return 'bigGroups';
      }
      if (!free.has(option.id)) {
        return 'taken';
      }
      const left = seatsLeftOf(option) ?? option.capacity;
      const fitsAlone = left === null || left >= people;
      return (fitsAlone && requirement.quantity <= 1) || suggestionWith(option.id) ? 'free' : 'small';
    }
    // On its own a small spot can never seat the group; with several spots combined it might.
    if (!many && !option.shared && option.minPartySize && people < option.minPartySize) {
      return 'bigGroups';
    }
    if (!many && option.capacity !== null && option.capacity < people) {
      return 'small';
    }
    if (!free.has(option.id)) {
      return 'taken';
    }
    const left = seatsLeftOf(option);
    return !many && left !== null && left < people ? 'small' : 'free';
  };

  const isSuggestionPicked = (allocation: AllocationOption, picked: number[]) =>
    allocation.resources.length === picked.length && allocation.resources.every((resource) => picked.includes(resource.id));

  /** The pick after a click on {@code option}; null when the click picks nothing. */
  const afterClick = (option: ChoosableOption, picked: number[]): number[] | null => {
    if (statusOf(option) !== 'free') {
      return null;
    }
    if (combined) {
      // Tables are never assembled by hand: a click picks the best way to seat
      // the group that uses this table — the table alone when it is enough.
      if (picked.includes(option.id)) {
        return [];
      }
      const left = seatsLeftOf(option) ?? option.capacity;
      const fitsAlone = (left === null || left >= people) && requirement.quantity <= 1;
      const suggestion = suggestionWith(option.id);
      return fitsAlone ? [option.id] : suggestion ? suggestion.resources.map((resource) => resource.id) : [];
    }
    if (picked.includes(option.id)) {
      return picked.filter((id) => id !== option.id);
    }
    if (!many) {
      return [option.id];
    }
    return picked.length >= requirement.quantity ? [...picked.slice(1), option.id] : [...picked, option.id];
  };

  return { combined, many, suggestions, seatsLeftOf, statusOf, isSuggestionPicked, afterClick };
}

export type SeatingRules = ReturnType<typeof seatingRules>;

/**
 * The picks without any resource that is no longer free (someone was
 * faster); null when nothing changed.
 */
export function withoutTakenPicks(
  requirements: ChoosableRequirement[],
  slot: Slot,
  picks: Record<number, number[]>,
): Record<number, number[]> | null {
  let changed = false;
  const next = { ...picks };
  requirements.forEach((requirement) => {
    const picked = next[requirement.requirementId];
    if (!picked) {
      return;
    }
    const stillFree = picked.filter((id) => freeResources(slot, requirement.requirementId).has(id));
    if (stillFree.length !== picked.length) {
      changed = true;
      // Half a combination would not seat the group: it goes as a whole.
      if (stillFree.length && !requirement.combinable) {
        next[requirement.requirementId] = stillFree;
      } else {
        delete next[requirement.requirementId];
      }
    }
  });
  return changed ? next : null;
}

export function statusLabel(status: SpotStatus, people: number, option: ChoosableOption, seatsLeft: number | null): string {
  switch (status) {
    case 'free':
      return seatsLeft !== null ? `${seatsLeft} seats free` : 'available';
    case 'taken':
      return 'booked';
    case 'bigGroups':
      return `for groups of ${option.minPartySize}+`;
    default:
      return seatsLeft !== null && option.capacity !== null && seatsLeft < option.capacity
        ? `only ${seatsLeft} seats left`
        : `too small for ${people}`;
  }
}

/** The second line on a table in the drawing. */
export function captionOf(option: ChoosableOption, status: SpotStatus, seatsLeft: number | null): string | undefined {
  if (status === 'taken') {
    return 'Booked';
  }
  if (status === 'bigGroups') {
    return `Groups of ${option.minPartySize}+`;
  }
  if (seatsLeft !== null && option.capacity !== null) {
    return seatsLeft < option.capacity ? `${seatsLeft} of ${option.capacity} free` : `${option.capacity} seats · shared`;
  }
  return option.capacity ? `${option.capacity} seats` : undefined;
}
