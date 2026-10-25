import { afterEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ToastProvider } from '@/components/ToastProvider';
import { AuthProvider } from '@/features/auth/AuthContext';
import { PlatformPage } from '@/pages/platform/PlatformPage';
import { PlatformRoute } from '@/routes/PlatformRoute';
import { fakeApi } from '@/test/fakeApi';
import type { CurrentUser, PlatformBusiness } from '@/types/api';

const ADMIN: CurrentUser = {
  id: 1,
  email: 'admin@example.com',
  fullName: 'Ana',
  activeBusiness: null,
  businesses: [],
  platformAdmin: true,
  platformView: false,
};

const BISTRO: PlatformBusiness = {
  id: 7,
  name: 'Harbor Bistro',
  slug: 'harbor-bistro',
  active: true,
  createdAt: '2026-10-01T10:00:00Z',
  ownerEmails: ['owner@bistro.test'],
  recentReservations: 12,
};

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <AuthProvider>
            <Routes>
              <Route path="/platform" element={<PlatformRoute><PlatformPage /></PlatformRoute>} />
              <Route path="/admin" element={<p>Business admin</p>} />
              <Route path="/login" element={<p>Sign in</p>} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe('PlatformPage', () => {
  let api: ReturnType<typeof fakeApi> | undefined;

  afterEach(() => api?.restore());

  it('is only for platform administrators', async () => {
    api = fakeApi({ 'GET /api/auth/session': { status: 200, data: { ...ADMIN, platformAdmin: false } } });
    renderAt('/platform');
    expect(await screen.findByText('Sign in')).toBeInTheDocument();
  });

  it('lists the businesses and opens one with owner rights', async () => {
    const owner = { ...ADMIN, platformView: true, activeBusiness: { businessId: 7, businessName: 'Harbor Bistro', businessSlug: 'harbor-bistro', role: 'BUSINESS_OWNER' as const } };
    api = fakeApi({
      'GET /api/auth/session': { status: 200, data: ADMIN },
      'GET /api/platform/businesses': { status: 200, data: [BISTRO] },
      'POST /api/platform/businesses/7/enter': { status: 200, data: { expiresAt: '2030-01-01T00:00:00Z', user: owner } },
    });
    renderAt('/platform?tab=businesses');

    expect(await screen.findByText('Harbor Bistro')).toBeInTheDocument();
    expect(screen.getByText('owner@bistro.test')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Open admin' }));

    expect(await screen.findByText('Business admin')).toBeInTheDocument();
    expect(api.requests.some((request) => request.method === 'POST' && request.url === '/api/platform/businesses/7/enter')).toBe(true);
  });
});
