import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '@/features/auth/AuthContext';
import { loadPendingSignup, savePendingSignup } from '@/features/auth/pendingSignup';
import { RegisterPage } from '@/pages/auth/RegisterPage';
import { fakeApi } from '@/test/fakeApi';

const OWNER = {
  id: 1,
  email: 'ana@example.com',
  fullName: 'Ana',
  activeBusiness: { businessId: 7, businessName: 'Fresh Cuts', businessSlug: 'fresh-cuts', role: 'BUSINESS_OWNER' },
  businesses: [],
};

function renderRegister() {
  render(
    <MemoryRouter initialEntries={['/register']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/admin" element={<p>Dashboard</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('RegisterPage after a reload', () => {
  let api: ReturnType<typeof fakeApi> | undefined;

  beforeEach(() => sessionStorage.clear());
  afterEach(() => api?.restore());

  it('comes back to the code step, and the code still signs up', async () => {
    savePendingSignup('owner', { email: 'ana@example.com', businessName: 'Fresh Cuts', existingAccount: true });
    api = fakeApi({
      'GET /api/auth/session': { status: 204 },
      'POST /api/auth/register/verify': { status: 201, data: { expiresAt: '2030-01-01T00:00:00Z', user: OWNER } },
    });
    renderRegister();

    expect(await screen.findByText('ana@example.com')).toBeInTheDocument();
    expect(screen.getByText(/Fresh Cuts is added to that account/)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/verification code/i), '123456');
    await userEvent.click(screen.getByRole('button', { name: /verify/i }));

    expect(await screen.findByText('Dashboard')).toBeInTheDocument();
    expect(api.requests.find((r) => r.url === '/api/auth/register/verify')?.data).toEqual({
      email: 'ana@example.com',
      code: '123456',
    });
    expect(loadPendingSignup('owner')).toBeNull();
  });

  it('remembers the sign-up once the code is sent, without the password', async () => {
    api = fakeApi({
      'GET /api/auth/session': { status: 204 },
      'POST /api/auth/register': { status: 202, data: { existingAccount: false } },
    });
    renderRegister();

    await userEvent.type(await screen.findByLabelText('Your name'), 'Ana');
    await userEvent.type(screen.getByLabelText('Business name'), 'Fresh Cuts');
    await userEvent.type(screen.getByLabelText('Email'), 'ana@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'secret-password');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => expect(loadPendingSignup('owner')?.email).toBe('ana@example.com'));
    expect(sessionStorage.getItem('pendingSignup:owner')).not.toContain('secret-password');
  });
});
