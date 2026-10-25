import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ReviewStep, type ReviewLine } from '@/features/public/wizard/ReviewStep';

const QUOTE = { originalPrice: 35, discountAmount: 0, finalPrice: 35, currency: 'EUR' };

function renderReview(lines: ReviewLine[], free: boolean) {
  render(
    <ReviewStep
      lines={lines}
      onEdit={vi.fn()}
      codeInput=""
      onCodeInput={vi.fn()}
      onApplyCode={vi.fn()}
      codeError={null}
      quote={free ? { ...QUOTE, originalPrice: 0, finalPrice: 0 } : QUOTE}
      free={free}
    />,
  );
}

describe('ReviewStep', () => {
  it('a paid booking shows the total and takes a discount code', () => {
    renderReview([{ label: 'Service', value: 'Dinner · 2 h', step: 'service' }], false);
    expect(screen.getByText('Total')).toBeInTheDocument();
    expect(screen.getByLabelText('Discount code')).toBeInTheDocument();
  });

  it('a free booking (a table) shows no price and no discount code', () => {
    renderReview([{ label: 'Booking', value: 'Table reservation · 2 h' }], true);
    expect(screen.getByText('Table reservation · 2 h')).toBeInTheDocument();
    expect(screen.queryByText('Total')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Discount code')).not.toBeInTheDocument();
  });

  it('a line with nothing else to choose has no "Change"', () => {
    renderReview(
      [
        { label: 'Booking', value: 'Table reservation · 2 h' },
        { label: 'When', value: 'Fri 1 May at 19:00', step: 'time' },
      ],
      true,
    );
    expect(screen.getAllByRole('button', { name: 'Change' })).toHaveLength(1);
  });
});
