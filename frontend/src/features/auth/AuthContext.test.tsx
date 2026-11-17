import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
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

const OTHER_OWNER: CurrentUser = {
  id: 2,
  email: 'luka@example.com',
  fullName: 'Luka Owner',
  activeBusiness: { businessId: 9, businessName: 'Harbor Bistro', businessSlug: 'harbor-bistro', role: 'BUSINESS_OWNER' },
  businesses: [],
};

let queryClient = new QueryClient();
const wrapper = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={queryClient}>
    <AuthProvider>{children}</AuthProvider>
  </QueryClientProvider>
);
/** Something the admin had loaded for the business signed in before. */
const cacheBusinessName = (name: string) => queryClient.setQueryData(['business'], { name });

describe('AuthProvider', () => {
  let api: ReturnType<typeof fakeApi> | undefined;

  beforeEach(() => {
    queryClient = new QueryClient();
  });
  afterEach(() => api?.restore());

  it('forgets the previous business’s data when another one signs up', async () => {
    api = fakeApi({
      'GET /api/auth/session': { status: 200, data: OWNER },
      'POST /api/auth/register/verify': { status: 201, data: { expiresAt: '2030-01-01T00:00:00Z', user: OTHER_OWNER } },
    });
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.user?.id).toBe(OWNER.id));
    cacheBusinessName('Serenity');

    await act(() => result.current.verifyRegistration('luka@example.com', '123456'));

    expect(result.current.user?.activeBusiness?.businessName).toBe('Harbor Bistro');
    expect(queryClient.getQueryData(['business'])).toBeUndefined();
  });

  it('forgets the business’s data on signing out', async () => {
    api = fakeApi({
      'GET /api/auth/session': { status: 200, data: OWNER },
      'POST /api/auth/logout': { status: 204 },
    });
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
    cacheBusinessName('Serenity');

    await act(() => result.current.logout());

    expect(queryClient.getQueryData(['business'])).toBeUndefined();
  });

  it('keeps what was loaded when the same account signs in again', async () => {
    api = fakeApi({
      'GET /api/auth/session': { status: 200, data: OWNER },
      'POST /api/auth/login': { status: 200, data: { expiresAt: '2030-01-01T00:00:00Z', user: OWNER } },
    });
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
    cacheBusinessName('Serenity');

    await act(() => result.current.login('owner@example.com', 'password123'));

    expect(queryClient.getQueryData(['business'])).toEqual({ name: 'Serenity' });
  });

  it('notices a sign-in made in another tab when this one is shown again', async () => {
    api = fakeApi({ 'GET /api/auth/session': { status: 200, data: OWNER } });
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.user?.id).toBe(OWNER.id));
    cacheBusinessName('Serenity');
    api.restore();
    api = fakeApi({ 'GET /api/auth/session': { status: 200, data: OTHER_OWNER } });

    act(() => {
      document.dispatchEvent(new Event('visibilitychange'));
    });

    await waitFor(() => expect(result.current.user?.id).toBe(OTHER_OWNER.id));
    expect(queryClient.getQueryData(['business'])).toBeUndefined();
  });

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
        businessType: 'SPA',
        currency: 'MKD',
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
