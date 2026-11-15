import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ResourceUtilizationCard } from '@/features/analytics/ResourceUtilizationCard';
import type { ResourceUtilization } from '@/types/api';

const RANGE = { from: '2030-05-01', to: '2030-05-31' };

function resource(id: number, typeId: number, typeName: string): ResourceUtilization {
  return {
    resourceId: id,
    resourceName: `${typeName} ${id}`,
    resourceTypeId: typeId,
    resourceTypeName: typeName,
    reservations: 3,
    bookedMinutes: 180,
    availableMinutes: 600,
    utilization: 0.3,
  };
}

// 12 tables and 3 rooms: more than one page at the default 10 rows.
const RESOURCES = [
  ...Array.from({ length: 12 }, (_, i) => resource(i + 1, 1, 'Table')),
  ...Array.from({ length: 3 }, (_, i) => resource(i + 100, 2, 'Room')),
];

function rowNames() {
  return screen.getAllByRole('row').slice(1).map((row) => within(row).getAllByRole('cell')[0].textContent);
}

describe('ResourceUtilizationCard', () => {
  it('shows ten resources at a time and pages through the rest', async () => {
    render(<ResourceUtilizationCard resources={RESOURCES} range={RANGE} />);

    expect(rowNames()).toHaveLength(10);
    expect(screen.getByText('1–10 of 15')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /next page/i }));
    expect(rowNames()).toHaveLength(5);
    expect(screen.getByText('11–15 of 15')).toBeInTheDocument();
  });

  it('narrows the list to one type of resource and starts again from the first page', async () => {
    render(<ResourceUtilizationCard resources={RESOURCES} range={RANGE} />);
    await userEvent.click(screen.getByRole('button', { name: /next page/i }));

    await userEvent.click(screen.getByRole('combobox', { name: 'Type' }));
    await userEvent.click(screen.getByRole('option', { name: 'Room' }));

    expect(rowNames().every((name) => name?.startsWith('Room'))).toBe(true);
    expect(rowNames()).toHaveLength(3);
    expect(screen.getByText('1–3 of 3')).toBeInTheDocument();
  });

  it('offers no type filter when there is only one type', () => {
    render(<ResourceUtilizationCard resources={RESOURCES.slice(0, 2)} range={RANGE} />);
    expect(screen.queryByRole('combobox', { name: 'Type' })).not.toBeInTheDocument();
  });
});
