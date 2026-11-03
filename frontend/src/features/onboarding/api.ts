import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import { useInvalidatingMutation } from '@/features/mutation';
import type { Onboarding, OnboardingStepKey } from '@/types/api';

/**
 * The "Getting started" checklist, or null for a business that has none.
 * Steps tick when the owner saves things on other pages, so it is fetched
 * fresh whenever the dashboard shows it.
 */
export function useOnboarding() {
  return useQuery({
    queryKey: queryKeys.onboarding,
    queryFn: async () => {
      const response = await apiClient.get<Onboarding | ''>('/api/admin/onboarding');
      return response.status === 204 || !response.data ? null : response.data;
    },
    refetchOnMount: 'always',
  });
}

export function useMarkOnboardingStep() {
  return useInvalidatingMutation(
    async (key: OnboardingStepKey) =>
      (await apiClient.post<Onboarding>(`/api/admin/onboarding/steps/${key}`)).data,
    [queryKeys.onboarding],
  );
}

export function useDismissOnboarding() {
  return useInvalidatingMutation(
    async () => (await apiClient.post<Onboarding>('/api/admin/onboarding/dismiss')).data,
    [queryKeys.onboarding],
  );
}
