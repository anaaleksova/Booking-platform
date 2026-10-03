import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import { useInvalidatingMutation } from '@/features/mutation';
import type { Discount, DiscountInput } from '@/types/api';

export function useDiscounts() {
  return useQuery({
    queryKey: queryKeys.discounts,
    queryFn: async () => (await apiClient.get<Discount[]>('/api/admin/discounts')).data,
  });
}

export function useSaveDiscount() {
  return useInvalidatingMutation(
    async ({ id, input }: { id?: number; input: DiscountInput }) =>
      id === undefined
        ? (await apiClient.post<Discount>('/api/admin/discounts', input)).data
        : (await apiClient.put<Discount>(`/api/admin/discounts/${id}`, input)).data,
    [queryKeys.discounts],
  );
}

export function useDeleteDiscount() {
  return useInvalidatingMutation(
    async (id: number) => {
      await apiClient.delete(`/api/admin/discounts/${id}`);
    },
    [queryKeys.discounts],
  );
}
