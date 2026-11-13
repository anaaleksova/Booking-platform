import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SpotStep } from '@/features/public/wizard/SpotStep';
import { fakeApi } from '@/test/fakeApi';
import type { AllocationOption, ChoosableOption, ChoosableRequirement, PublicService, ResourceOption } from '@/types/api';

const option = (id: number, capacity: number, extra: Partial<ChoosableOption> = {}): ChoosableOption => ({
  id,
  name: `Table ${id}`,
  capacity,
  shared: false,
  minPartySize: null,
  ...extra,
});

const OPTIONS = [
  option(1, 2),
  option(2, 4),
  option(3, 6, { minPartySize: 5 }),
  option(4, 4),
  option(5, 8, { shared: true }),
  option(6, 4),
];

function requirement(combinable: boolean): ChoosableRequirement {
  return {
    requirementId: 9,
    resourceTypeName: 'Table',
    quantity: 1,
    combinable,
    resources: OPTIONS,
    floorPlans: [
      {
        id: 1,
        name: 'Dining room',
        aspectRatio: 1.6,
        // Table 6 is left off the map.
        spots: [1, 2, 3, 4, 5].map((id) => ({ resourceId: id, x: 10 + id * 12, y: 50, width: 10, height: 16, shape: 'ROUND' as const })),
        decor: [],
      },
    ],
  };
}

const SERVICE = (combinable = false): PublicService => ({
  id: 7,
  name: 'Dinner',
  durationMinutes: 90,
  price: 0,
  pricePerParticipant: false,
  maxParticipants: 12,
  approvalRequired: false,
  currency: 'EUR',
  choosableRequirements: [requirement(combinable)],
});

/** Free at 19:00: everything but Table 4; the shared table has 3 seats left. */
function availability(free: ResourceOption[], allocations: AllocationOption[] = []) {
  return {
    status: 200,
    data: [
      {
        date: '2030-05-01',
        slots: [
          {
            time: '19:00:00',
            startTime: '2030-05-01T19:00:00',
            endTime: '2030-05-01T20:30:00',
            requirements: [
              { requirementId: 9, resourceTypeId: 3, resourceTypeName: 'Table', quantity: 1, required: true, customerChoosable: true, combinable: false, resources: free, allocations },
            ],
          },
        ],
      },
    ],
  };
}

const FREE: ResourceOption[] = [
  { id: 1, name: 'Table 1', capacity: 2 },
  { id: 2, name: 'Table 2', capacity: 4 },
  { id: 3, name: 'Table 3', capacity: 6 },
  { id: 5, name: 'Table 5', capacity: 8, seatsLeft: 3 },
  { id: 6, name: 'Table 6', capacity: 4 },
];

describe('SpotStep', () => {
  let api: ReturnType<typeof fakeApi> | undefined;
  afterEach(() => api?.restore());

  function renderStep({ people = 4, picks = {}, combinable = false, free = FREE, allocations = [] as AllocationOption[] } = {}) {
    api = fakeApi({ 'GET /api/public/businesses/harbor/services/7/availability': availability(free, allocations) });
    const onPicks = vi.fn();
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <SpotStep
          slug="harbor"
          service={SERVICE(combinable)}
          people={people}
          date="2030-05-01"
          time="19:00"
          picks={picks}
          onPicks={onPicks}
          onSlotGone={() => undefined}
        />
      </QueryClientProvider>,
    );
    return onPicks;
  }

  const spot = (name: RegExp) => screen.findByRole('button', { name });

  it('shows what each table means for this group', async () => {
    renderStep();

    expect(await spot(/^Table 2, 4 seats — available/)).toHaveAttribute('aria-pressed', 'false');
    expect(await spot(/^Table 1, 2 seats — too small for 4/)).toHaveAttribute('aria-disabled', 'true');
    expect(await spot(/^Table 3, 6 seats — for groups of 5\+/)).toHaveAttribute('aria-disabled', 'true');
    expect(await spot(/^Table 5, 8 seats, shared — only 3 seats left/)).toHaveAttribute('aria-disabled', 'true');
    // Booked: shown, but not a button at all.
    expect(screen.getByLabelText(/^Table 4, 4 seats — booked/)).toHaveAttribute('aria-disabled', 'true');
    expect(screen.queryByRole('button', { name: /^Table 4/ })).not.toBeInTheDocument();
  });

  it('picks a free table, and "No preference" clears it', async () => {
    const onPicks = renderStep();

    await userEvent.click(await spot(/^Table 2, 4 seats/));
    expect(onPicks).toHaveBeenLastCalledWith({ 9: [2] });

    await userEvent.click(screen.getByRole('button', { name: /No preference/ }));
    expect(onPicks).toHaveBeenLastCalledWith({});
  });

  it('clicking a table that cannot seat the group picks nothing', async () => {
    const onPicks = renderStep();
    await userEvent.click(await spot(/^Table 1, 2 seats/));
    expect(onPicks).not.toHaveBeenCalled();
  });

  it('offers free tables that are not on the map', async () => {
    const onPicks = renderStep();
    await userEvent.click(await screen.findByRole('button', { name: 'Table 6 · 4 seats' }));
    expect(onPicks).toHaveBeenLastCalledWith({ 9: [6] });
  });

  it('drops a pick that someone else has just taken', async () => {
    const onPicks = renderStep({ picks: { 9: [4] } });
    await waitFor(() => expect(onPicks).toHaveBeenCalledWith({}));
  });

  it('the list view offers the same choices', async () => {
    const onPicks = renderStep();
    await userEvent.click(await screen.findByRole('button', { name: 'List view' }));

    const table1 = screen.getByRole('button', { name: /Table 1.*too small for 4/ });
    expect(table1).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: /^T Table 2/ }));
    expect(onPicks).toHaveBeenLastCalledWith({ 9: [2] });
  });

  it('a table that is part of a combination picks the whole combination', async () => {
    const onPicks = renderStep({
      people: 6,
      combinable: true,
      allocations: [{ resources: [FREE[0], FREE[1]], capacity: 6, area: 'Dining room' }],
    });

    expect(await screen.findByText(/^Best fit for 6 people/)).toBeInTheDocument();
    await userEvent.click(await spot(/^Table 1, 2 seats — available/));
    expect(onPicks).toHaveBeenLastCalledWith({ 9: [1, 2] });
  });
});
