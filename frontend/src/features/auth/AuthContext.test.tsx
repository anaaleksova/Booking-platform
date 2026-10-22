import { afterEach, describe, expect, it } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { AuthProvider, useAuth } from '@/features/auth/AuthContext';
import { fakeApi } from '@/test/fakeApi';
import type { CurrentUser } from '@/types/api';

const OWNER: CurrentUser = {
  id: 1,
  email: 'owner@example.com',
  fullName: 'Ana Owner',
  activeBusiness: { businessId: 7, businessName: 'Serenity', businessSlug: 'serenity', role: 'BUSINESS_OWNER' },
  businesses: [],
};

const wrapper = ({ children }: { children: ReactNode }) => <AuthProvider>{children}</AuthProvider>;

describe('AuthProvider', () => {
  let api: ReturnType<typeof fakeApi> | undefined;

  afterEach(() => api?.restore());

  it('starts signed out when the server has no session', async () => {
    api = fakeApi({ 'GET /api/auth/session': { status: 204 } });

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.initialising).toBe(false));
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('restores the session the cookie belongs to', async () => {
    api = fakeApi({ 'GET /api/auth/session': { status: 200, data: OWNER } });

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.user?.email).toBe('owner@example.com'));
    expect(result.current.isOwner).toBe(true);
  });

  it('registers a business in two steps: the code, then the session', async () => {
    api = fakeApi({
      'GET /api/auth/session': { status: 204 },
      'POST /api/auth/register': { status: 202 },
      'POST /api/auth/register/verify': { status: 201, data: { expiresAt: '2030-01-01T00:00:00Z', user: OWNER } },
    });
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.initialising).toBe(false));

    await act(() =>
      result.current.register({
        fullName: 'Ana Owner',
        email: 'owner@example.com',
        password: 'password123',
        businessName: 'Serenity',
        timezone: 'Europe/Skopje',
      }),
    );
    expect(result.current.isAuthenticated).toBe(false);

    await act(() => result.current.verifyRegistration('owner@example.com', '123456'));
    expect(result.current.user?.activeBusiness?.businessName).toBe('Serenity');
    expect(api.requests.at(-1)?.data).toEqual({ email: 'owner@example.com', code: '123456' });
  });

  it('signs out here at once and asks the server to clear the cookie', async () => {
    api = fakeApi({
      'GET /api/auth/session': { status: 200, data: OWNER },
      'POST /api/auth/logout': { status: 204 },
    });
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));

    await act(() => result.current.logout());

    expect(result.current.isAuthenticated).toBe(false);
    expect(api.requests.map((request) => `${request.method} ${request.url}`)).toContain('POST /api/auth/logout');
  });

  it('never keeps a token in script-readable storage', async () => {
    api = fakeApi({
      'GET /api/auth/session': { status: 204 },
      'POST /api/auth/login': { status: 200, data: { expiresAt: '2030-01-01T00:00:00Z', user: OWNER } },
    });
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.initialising).toBe(false));

    await act(() => result.current.login('owner@example.com', 'password123'));

    expect(result.current.isAuthenticated).toBe(true);
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });
});
