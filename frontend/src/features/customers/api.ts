import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import { useInvalidatingMutation } from '@/features/mutation';
import type { Customer, CustomerHistory, Page } from '@/types/api';

export interface CustomerInput {
  firstName: string;
  lastName?: string;
  email?: string;
  phone?: string;
  notes?: string;
}

export function useCustomers(query: string, page: number, size = 25) {
  return useQuery({
    queryKey: queryKeys.customers(query, page),
    queryFn: async () =>
      (await apiClient.get<Page<Customer>>('/api/admin/customers', { params: { query: query || undefined, page, size } }))
        .data,
    placeholderData: keepPreviousData,
  });
}

export function useCustomer(id: number) {
  return useQuery({
    queryKey: queryKeys.customer(id),
    queryFn: async () => (await apiClient.get<Customer>(`/api/admin/customers/${id}`)).data,
  });
}

export function useCustomerHistory(id: number) {
  return useQuery({
    queryKey: queryKeys.customerHistory(id),
    queryFn: async () => (await apiClient.get<CustomerHistory>(`/api/admin/customers/${id}/reservations`)).data,
  });
}

export function useSaveCustomer() {
  return useInvalidatingMutation(
    async ({ id, input }: { id?: number; input: CustomerInput }) =>
      id === undefined
        ? (await apiClient.post<Customer>('/api/admin/customers', input)).data
        : (await apiClient.put<Customer>(`/api/admin/customers/${id}`, input)).data,
    [['customers']],
  );
}
