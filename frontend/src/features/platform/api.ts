import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { useAuth } from '@/features/auth/AuthContext';
import { useInvalidatingMutation } from '@/features/mutation';
import { queryKeys } from '@/features/queryKeys';
import type { AuthResponse, PlatformAccount, PlatformActivity, PlatformBusiness, PlatformOverview } from '@/types/api';

export function usePlatformOverview() {
  return useQuery({
    queryKey: queryKeys.platformOverview,
    queryFn: async () => (await apiClient.get<PlatformOverview>('/api/platform/overview')).data,
  });
}

export function usePlatformBusinesses(query: string) {
  return useQuery({
    queryKey: queryKeys.platformBusinesses(query),
    queryFn: async () =>
      (await apiClient.get<PlatformBusiness[]>('/api/platform/businesses', { params: { query } })).data,
    placeholderData: (previous) => previous,
  });
}

export function usePlatformAccounts(query: string) {
  return useQuery({
    queryKey: queryKeys.platformAccounts(query),
    queryFn: async () => (await apiClient.get<PlatformAccount[]>('/api/platform/accounts', { params: { query } })).data,
    placeholderData: (previous) => previous,
  });
}

export function usePlatformActivity() {
  return useQuery({
    queryKey: queryKeys.platformActivity,
    queryFn: async () => (await apiClient.get<PlatformActivity[]>('/api/platform/activity')).data,
  });
}

export function useSetBusinessActive() {
  return useInvalidatingMutation(
    async ({ id, active }: { id: number; active: boolean }) =>
      (await apiClient.put<PlatformBusiness>(`/api/platform/businesses/${id}/active`, { active })).data,
    [['platform']],
  );
}

export function useSetAccountActive() {
  return useInvalidatingMutation(
    async ({ id, active }: { id: number; active: boolean }) => {
      await apiClient.put(`/api/platform/accounts/${id}/active`, { active });
    },
    [['platform']],
  );
}

/**
 * Entering a business's admin, and leaving it again. Each is a new session for
 * another business, so everything cached for the previous one is dropped.
 */
export function usePlatformSession() {
  const { adoptSession } = useAuth();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const switchTo = useCallback(
    (session: AuthResponse, to: string) => {
      queryClient.clear();
      adoptSession(session);
      navigate(to, { replace: true });
    },
    [adoptSession, queryClient, navigate],
  );

  const enter = useCallback(
    async (businessId: number) =>
      switchTo((await apiClient.post<AuthResponse>(`/api/platform/businesses/${businessId}/enter`)).data, '/admin'),
    [switchTo],
  );

  /** Back to the administrator's own session; `to` '/admin' opens their own business, if any. */
  const exit = useCallback(
    async (to: '/platform' | '/admin' = '/platform') =>
      switchTo((await apiClient.post<AuthResponse>('/api/platform/exit')).data, to),
    [switchTo],
  );

  return { enter, exit };
}
