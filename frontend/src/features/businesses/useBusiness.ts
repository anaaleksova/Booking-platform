import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import type { Business, BusinessSettings, DayInterval } from '@/types/api';

export interface BusinessInput {
  name: string;
  slug: string;
  description?: string;
  logoUrl?: string;
  phone?: string;
  email?: string;
  address?: string;
  timezone: string;
}

export function useBusiness() {
  return useQuery({
    queryKey: queryKeys.business,
    queryFn: async () => {
      const { data } = await apiClient.get<Business>('/api/business');
      return data;
    },
  });
}

export function useUpdateBusiness() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: BusinessInput) => {
      const { data } = await apiClient.put<Business>('/api/business', input);
      return data;
    },
    onSuccess: (business) => {
      queryClient.setQueryData(queryKeys.business, business);
      // The timezone affects every rendered time.
      void queryClient.invalidateQueries({ queryKey: ['bookings'] });
      void queryClient.invalidateQueries({ queryKey: ['availability'] });
    },
  });
}

export function useBusinessSettings() {
  return useQuery({
    queryKey: queryKeys.businessSettings,
    queryFn: async () => {
      const { data } = await apiClient.get<BusinessSettings>('/api/business/settings');
      return data;
    },
  });
}

export function useUpdateBusinessSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: BusinessSettings) => {
      const { data } = await apiClient.put<BusinessSettings>('/api/business/settings', input);
      return data;
    },
    onSuccess: (settings) => {
      queryClient.setQueryData(queryKeys.businessSettings, settings);
      // Slot interval and notice rules change what the pickers offer.
      void queryClient.invalidateQueries({ queryKey: ['availability'] });
    },
  });
}

export function useWorkingHours() {
  return useQuery({
    queryKey: queryKeys.workingHours,
    queryFn: async () => {
      const { data } = await apiClient.get<DayInterval[]>('/api/working-hours');
      return data;
    },
  });
}

export function useReplaceWorkingHours() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (intervals: DayInterval[]) => {
      const { data } = await apiClient.put<DayInterval[]>('/api/working-hours', intervals);
      return data;
    },
    onSuccess: (intervals) => {
      queryClient.setQueryData(queryKeys.workingHours, intervals);
      void queryClient.invalidateQueries({ queryKey: ['availability'] });
    },
  });
}
