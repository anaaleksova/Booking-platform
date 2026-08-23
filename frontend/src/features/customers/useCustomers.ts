import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import type { Customer, CustomerHistory, Page } from '@/types/api';

export interface CustomerInput {
  name: string;
  phone?: string;
  email?: string;
  notes?: string;
}

export function useCustomers(query: string, page: number, size = 25) {
  return useQuery({
    queryKey: queryKeys.customers(query, page),
    queryFn: async () => {
      const { data } = await apiClient.get<Page<Customer>>('/api/customers', {
        params: { query: query || undefined, page, size },
      });
      return data;
    },
    // Keeps the table on screen while a search term is being typed.
    placeholderData: (previous) => previous,
  });
}

export function useCustomer(id: number) {
  return useQuery({
    queryKey: queryKeys.customer(id),
    queryFn: async () => {
      const { data } = await apiClient.get<Customer>(`/api/customers/${id}`);
      return data;
    },
  });
}

export function useCustomerHistory(id: number) {
  return useQuery({
    queryKey: queryKeys.customerHistory(id),
    queryFn: async () => {
      const { data } = await apiClient.get<CustomerHistory>(`/api/customers/${id}/history`);
      return data;
    },
  });
}

function useCustomerInvalidation() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ['customers'] });
}

export function useCreateCustomer() {
  const invalidate = useCustomerInvalidation();
  return useMutation({
    mutationFn: async (input: CustomerInput) => {
      const { data } = await apiClient.post<Customer>('/api/customers', input);
      return data;
    },
    onSuccess: () => void invalidate(),
  });
}

export function useUpdateCustomer() {
  const invalidate = useCustomerInvalidation();
  return useMutation({
    mutationFn: async ({ id, input }: { id: number; input: CustomerInput }) => {
      const { data } = await apiClient.put<Customer>(`/api/customers/${id}`, input);
      return data;
    },
    onSuccess: () => void invalidate(),
  });
}

export function useDeleteCustomer() {
  const invalidate = useCustomerInvalidation();
  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/api/customers/${id}`);
    },
    onSuccess: () => void invalidate(),
  });
}
