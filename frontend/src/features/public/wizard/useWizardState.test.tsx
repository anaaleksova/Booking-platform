import { describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import type { ChoosableRequirement, PublicService, Slot } from '@/types/api';
import {
  needsOptions,
  selectionsFrom,
  slotFitsPicks,
  stepTitle,
  stepsFor,
  useWizardState,
  withoutMappedPicks,
} from '@/features/public/wizard/useWizardState';

function requirement(id: number, overrides: Partial<ChoosableRequirement> = {}): ChoosableRequirement {
  return {
    requirementId: id,
    resourceTypeName: 'Therapist',
    quantity: 1,
    combinable: false,
    resources: [],
    floorPlans: [],
    ...overrides,
  };
}

function service(overrides: Partial<PublicService> = {}): PublicService {
  return {
    id: 1,
    name: 'Massage',
    durationMinutes: 60,
    price: 50,
    pricePerParticipant: false,
    maxParticipants: 1,
    currency: 'EUR',
    choosableRequirements: [],
    ...overrides,
  };
}

const MAPPED = requirement(9, {
  resourceTypeName: 'Table',
  floorPlans: [{ id: 1, name: 'Dining room', aspectRatio: 1.5, spots: [], decor: [] }] as unknown as ChoosableRequirement['floorPlans'],
});

describe('wizard steps', () => {
  it('a single-person service with nothing to choose skips straight to the time', () => {
    expect(stepsFor(service())).toEqual(['service', 'time', 'details', 'review']);
  });

  it('groups and listed choices add the options step', () => {
    expect(needsOptions(service({ maxParticipants: 4 }))).toBe(true);
    expect(stepsFor(service({ choosableRequirements: [requirement(3)] }))).toContain('options');
  });

  it('a requirement with a floor plan adds the map step after the time', () => {
    const steps = stepsFor(service({ choosableRequirements: [MAPPED] }));
    expect(steps.indexOf('spot')).toBe(steps.indexOf('time') + 1);
    expect(stepTitle('spot', service({ choosableRequirements: [MAPPED] }))).toBe('Choose your table');
  });

  it('changing the time forgets map picks but keeps the rest', () => {
    const withBoth = service({ choosableRequirements: [requirement(3), MAPPED] });
    expect(withoutMappedPicks(withBoth, { 3: [11], 9: [21] })).toEqual({ 3: [11] });
  });
});

describe('selectionsFrom', () => {
  it('sends only complete picks', () => {
    const pair = service({ choosableRequirements: [requirement(3, { quantity: 2 })] });
    expect(selectionsFrom(pair, { 3: [11] })).toEqual([]);
    expect(selectionsFrom(pair, { 3: [11, 12] })).toEqual([{ requirementId: 3, resourceIds: [11, 12] }]);
  });

  it('a combinable requirement takes any number from its minimum up', () => {
    const tables = service({ choosableRequirements: [requirement(3, { combinable: true })] });
    expect(selectionsFrom(tables, { 3: [11, 12, 13] })).toHaveLength(1);
  });
});

describe('slotFitsPicks', () => {
  const slot = {
    requirements: [{ requirementId: 3, resources: [{ id: 11 }, { id: 12 }] }],
  } as unknown as Slot;

  it('fits when every picked resource is free', () => {
    expect(slotFitsPicks(slot, [{ requirementId: 3, resourceIds: [12] }])).toBe(true);
  });

  it('does not fit when a pick is taken', () => {
    expect(slotFitsPicks(slot, [{ requirementId: 3, resourceIds: [13] }])).toBe(false);
  });
});

describe('useWizardState', () => {
  function setup(initial: string) {
    let search = '';
    const wrapper = ({ children }: { children: ReactNode }) => (
      <MemoryRouter initialEntries={[initial]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{children}</MemoryRouter>
    );
    const { result } = renderHook(
      () => {
        search = useLocation().search;
        return useWizardState();
      },
      { wrapper },
    );
    return { result, search: () => search };
  }

  it('reads the choices from the URL', () => {
    const { result } = setup('/b/spa/book?service=4&people=2&pick=3:11,12&date=2030-05-01&time=10:00&step=time');
    expect(result.current.choices).toEqual({
      serviceId: 4,
      people: 2,
      picks: { 3: [11, 12] },
      date: '2030-05-01',
      time: '10:00',
    });
    expect(result.current.step).toBe('time');
  });

  it('ignores malformed picks and nonsense group sizes', () => {
    const { result } = setup('/b/spa/book?people=-3&pick=x:1;3:a,12');
    expect(result.current.choices.people).toBe(1);
    expect(result.current.choices.picks).toEqual({ 3: [12] });
  });

  it('writes a step and its choices back to the URL', () => {
    window.scrollTo = () => undefined;
    const { result, search } = setup('/b/spa/book?service=4');
    act(() => result.current.go('details', { date: '2030-05-01', time: '10:00', picks: { 3: [11] } }));

    const params = new URLSearchParams(search());
    expect(params.get('service')).toBe('4');
    expect(params.get('pick')).toBe('3:11');
    expect(params.get('step')).toBe('details');
    expect(result.current.choices.time).toBe('10:00');
  });
});
