import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import type { AnalyticsOverview, CustomerAnalyticsRow, Page, ServiceRevenue } from '@/types/api';

export interface AnalyticsRange {
  from?: string;
  to?: string;
}

export function useAnalyticsOverview({ from, to }: AnalyticsRange) {
  return useQuery({
    queryKey: queryKeys.analyticsOverview(from, to),
    queryFn: async () => {
      const { data } = await apiClient.get<AnalyticsOverview>('/api/analytics/overview', {
        params: { from, to },
      });
      return data;
    },
  });
}

export function useRevenueByService({ from, to }: AnalyticsRange) {
  return useQuery({
    queryKey: queryKeys.analyticsRevenueByService(from, to),
    queryFn: async () => {
      const { data } = await apiClient.get<ServiceRevenue[]>('/api/analytics/revenue-by-service', {
        params: { from, to },
      });
      return data;
    },
  });
}

export function useCustomerAnalytics({ from, to }: AnalyticsRange, page: number, size = 25) {
  return useQuery({
    queryKey: queryKeys.analyticsCustomers(from, to, page),
    queryFn: async () => {
      const { data } = await apiClient.get<Page<CustomerAnalyticsRow>>('/api/analytics/customers', {
        params: { from, to, page, size },
      });
      return data;
    },
    placeholderData: (previous) => previous,
  });
}
