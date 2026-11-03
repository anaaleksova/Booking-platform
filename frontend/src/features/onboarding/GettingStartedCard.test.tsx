import { afterEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '@/components/ToastProvider';
import { GettingStartedCard } from '@/features/onboarding/GettingStartedCard';
import { fakeApi } from '@/test/fakeApi';
import type { Onboarding, OnboardingStepKey } from '@/types/api';

function onboarding(done: OnboardingStepKey[] = [], overrides: Partial<Onboarding> = {}): Onboarding {
  const keys: OnboardingStepKey[] = ['SERVICES', 'RESOURCES', 'HOURS', 'CONTACT', 'PHOTO', 'SHARE'];
  return {
    businessType: 'RESTAURANT',
    slug: 'harbor-bistro',
    steps: keys.map((key) => ({ key, done: done.includes(key) })),
    dismissed: false,
    ...overrides,
  };
}

function renderCard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <GettingStartedCard />
          <p>Dashboard</p>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('GettingStartedCard', () => {
  let api: ReturnType<typeof fakeApi> | undefined;
  afterEach(() => api?.restore());

  it('shows progress and speaks the business type', async () => {
    api = fakeApi({ 'GET /api/admin/onboarding': { status: 200, data: onboarding(['HOURS', 'CONTACT']) } });
    renderCard();

    expect(await screen.findByText(/2 of 6 done/)).toBeInTheDocument();
    expect(screen.getByText('Check your table reservation')).toBeInTheDocument();
    expect(screen.getByText('Set up your tables')).toBeInTheDocument();
    expect(within(screen.getByTestId('step-RESOURCES')).getByRole('link', { name: 'Open' })).toHaveAttribute(
      'href',
      '/admin/resources',
    );
    // Contact details tick themselves; there is nothing to confirm by hand.
    expect(within(screen.getByTestId('step-PHOTO')).queryByRole('button', { name: 'Looks good' })).toBeNull();
  });

  it('ticks a step the owner says looks good', async () => {
    let state = onboarding();
    api = fakeApi({
      'GET /api/admin/onboarding': () => ({ status: 200, data: state }),
      'POST /api/admin/onboarding/steps/SERVICES': () => {
        state = onboarding(['SERVICES']);
        return { status: 200, data: state };
      },
    });
    renderCard();

    await userEvent.click(
      within(await screen.findByTestId('step-SERVICES')).getByRole('button', { name: 'Looks good' }),
    );

    expect(await screen.findByText(/1 of 6 done/)).toBeInTheDocument();
    expect(within(screen.getByTestId('step-SERVICES')).getByLabelText('Done')).toBeInTheDocument();
  });

  it('goes away when hidden', async () => {
    let state = onboarding();
    api = fakeApi({
      'GET /api/admin/onboarding': () => ({ status: 200, data: state }),
      'POST /api/admin/onboarding/dismiss': () => {
        state = onboarding([], { dismissed: true });
        return { status: 200, data: state };
      },
    });
    renderCard();

    await userEvent.click(await screen.findByRole('button', { name: 'Hide' }));

    await waitFor(() => expect(screen.queryByText('Getting started')).toBeNull());
  });

  it('shows nothing for a business without a checklist, or one that is finished', async () => {
    api = fakeApi({ 'GET /api/admin/onboarding': { status: 204 } });
    renderCard();
    await waitFor(() => expect(api!.requests).toHaveLength(1));
    expect(screen.queryByText('Getting started')).toBeNull();
  });
});
