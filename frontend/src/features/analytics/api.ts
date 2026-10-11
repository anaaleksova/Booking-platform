import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import type {
  CustomerReport,
  Dashboard,
  Granularity,
  ReservationReport,
  ResourceUtilization,
  RevenuePeriods,
  RevenueReport,
  ServiceStat,
  SourceStat,
} from '@/types/api';

/** An inclusive range of business-local dates, "YYYY-MM-DD". */
export interface DateRange {
  from: string;
  to: string;
}

function rangeQuery<T>(key: readonly unknown[], path: string, params: object) {
  return {
    queryKey: key,
    queryFn: async () => (await apiClient.get<T>(path, { params })).data,
    placeholderData: keepPreviousData,
  };
}

export function useDashboard() {
  return useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: async () => (await apiClient.get<Dashboard>('/api/admin/dashboard')).data,
  });
}

export function useRevenuePeriods() {
  return useQuery({
    queryKey: queryKeys.revenuePeriods,
    queryFn: async () => (await apiClient.get<RevenuePeriods>('/api/admin/analytics/revenue/periods')).data,
  });
}

export function useRevenue(range: DateRange, granularity: Granularity) {
  const params = { ...range, granularity };
  return useQuery(rangeQuery<RevenueReport>(queryKeys.revenue(params), '/api/admin/analytics/revenue', params));
}

export function useReservationStats(range: DateRange) {
  return useQuery(
    rangeQuery<ReservationReport>(queryKeys.reservationStats(range), '/api/admin/analytics/reservations', range),
  );
}

export function useServiceStats(range: DateRange) {
  return useQuery(rangeQuery<ServiceStat[]>(queryKeys.serviceStats(range), '/api/admin/analytics/services', range));
}

export function useResourceStats(range: DateRange) {
  return useQuery(
    rangeQuery<ResourceUtilization[]>(queryKeys.resourceStats(range), '/api/admin/analytics/resources', range),
  );
}

export function useCustomerStats(range: DateRange) {
  return useQuery(
    rangeQuery<CustomerReport>(queryKeys.customerStats(range), '/api/admin/analytics/customers', range),
  );
}

export function useSourceStats(range: DateRange) {
  return useQuery(rangeQuery<SourceStat[]>(queryKeys.sourceStats(range), '/api/admin/analytics/sources', range));
}
