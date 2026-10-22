import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '@/components/ToastProvider';
import { FloorPlanEditor } from '@/features/floorplan/FloorPlanEditor';
import { fakeApi, type RecordedRequest } from '@/test/fakeApi';
import type { FloorPlan, Resource, ResourceType } from '@/types/api';

const TABLES: ResourceType = {
  id: 5,
  name: 'Table',
  employeeType: false,
  active: true,
  resourceCount: 2,
  floorPlanCount: 1,
};

function table(id: number, name: string, capacity: number): Resource {
  return {
    id,
    resourceTypeId: 5,
    name,
    capacity,
    sharedSeating: false,
    minPartySize: null,
    active: true,
    employeeManaged: false,
    serviceIds: [],
    hours: [],
  } as unknown as Resource;
}

const SAVED_PLAN: FloorPlan = {
  id: 40,
  resourceTypeId: 5,
  name: 'Dining room',
  aspectRatio: 1.6,
  items: [{ resourceId: 1, x: 20, y: 30, width: 11, height: 18, shape: 'ROUND' }],
  decor: [],
};

interface SavedFloor {
  id?: number;
  name: string;
  aspectRatio: number;
  items: { resourceId: number; x: number; y: number }[];
  decor: { kind: string }[];
}

describe('FloorPlanEditor', () => {
  let api: ReturnType<typeof fakeApi>;
  let saved: SavedFloor[] | null;

  beforeEach(() => {
    saved = null;
    api = fakeApi({
      'GET /api/admin/resource-types/5/floor-plans': { status: 200, data: [SAVED_PLAN] },
      'GET /api/admin/resources': { status: 200, data: [table(1, 'Table 1', 2), table(2, 'Table 2', 6)] },
      'PUT /api/admin/resource-types/5/floor-plans': (request: RecordedRequest) => {
        saved = (request.data as { plans: SavedFloor[] }).plans;
        return { status: 200, data: [] };
      },
    });
  });

  afterEach(() => api.restore());

  function renderEditor() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <ToastProvider>
          <FloorPlanEditor type={TABLES} onClose={() => undefined} />
        </ToastProvider>
      </QueryClientProvider>,
    );
  }

  it('shows the saved floor and what is not placed yet', async () => {
    renderEditor();

    expect(await screen.findByDisplayValue('Dining room')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Table 1 — drag/ })).toBeInTheDocument();
    expect(screen.getByText(/Table 2 · 6 seats/)).toBeInTheDocument();
  });

  it('places a table, nudges it with the keyboard and saves the floor', async () => {
    renderEditor();
    await screen.findByDisplayValue('Dining room');

    await userEvent.click(screen.getByRole('button', { name: 'Place' }));
    const placed = screen.getByRole('button', { name: /Table 2 — drag/ });
    fireEvent.keyDown(placed, { key: 'ArrowRight' });
    fireEvent.keyDown(placed, { key: 'ArrowDown', shiftKey: true });
    await userEvent.click(screen.getByRole('button', { name: 'Done' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(saved).not.toBeNull());
    const [floor] = saved!;
    expect(floor.id).toBe(40);
    expect(floor.name).toBe('Dining room');
    const table2 = floor.items.find((item) => item.resourceId === 2)!;
    // A first table lands at 30 % + offset 5 for the one already there, then +0.5 right and +5 down.
    expect(table2.x).toBe(35.5);
    expect(table2.y).toBe(45);
    expect(floor.items.find((item) => item.resourceId === 1)).toMatchObject({ x: 20, y: 30 });
  });

  it('adds a floor and draws on it separately', async () => {
    renderEditor();
    await screen.findByDisplayValue('Dining room');

    await userEvent.click(screen.getByRole('button', { name: /add floor/i }));
    const name = screen.getByLabelText('Floor name');
    expect(name).toHaveValue('Floor 2');
    await userEvent.clear(name);
    await userEvent.type(name, 'Rooftop');
    await userEvent.click(screen.getByRole('button', { name: /a wall or partition/i }));
    await userEvent.click(screen.getByRole('button', { name: 'Done' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(saved).not.toBeNull());
    expect(saved!.map((floor) => floor.name)).toEqual(['Dining room', 'Rooftop']);
    expect(saved![1].decor.map((piece) => piece.kind)).toEqual(['WALL']);
    expect(saved![1].items).toEqual([]);
  });

  it('will not save two floors with the same name', async () => {
    renderEditor();
    await screen.findByDisplayValue('Dining room');

    await userEvent.click(screen.getByRole('button', { name: /add floor/i }));
    const name = screen.getByLabelText('Floor name');
    await userEvent.clear(name);
    await userEvent.type(name, 'dining room');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(/Two floors are called/)).toBeInTheDocument();
    expect(saved).toBeNull();
  });

  it('deleting a table takes it off the plan', async () => {
    renderEditor();
    const placed = await screen.findByRole('button', { name: /Table 1 — drag/ });

    fireEvent.keyDown(placed, { key: 'Delete' });
    expect(screen.queryByRole('button', { name: /Table 1 — drag/ })).not.toBeInTheDocument();
    const notPlaced = screen.getByText('Not on any floor yet').closest('section')!;
    expect(within(notPlaced).getByText(/Table 1/)).toBeInTheDocument();
  });
});
