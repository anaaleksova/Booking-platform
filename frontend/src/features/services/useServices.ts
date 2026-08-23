import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import type { Service } from '@/types/api';

export interface ServiceInput {
  name: string;
  description?: string;
  durationMinutes: number;
  price?: number | null;
  currency: string;
  bufferBeforeMinutes: number;
  bufferAfterMinutes: number;
  requiresStaff: boolean;
  requiresResource: boolean;
  active: boolean;
}

export function useServices(activeOnly = false) {
  return useQuery({
    queryKey: queryKeys.services(activeOnly),
    queryFn: async () => {
      const { data } = await apiClient.get<Service[]>('/api/services', { params: { activeOnly } });
      return data;
    },
  });
}

/** Any change to the catalogue can change what is bookable, so availability is dropped too. */
function useCatalogueInvalidation() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['services'] });
    void queryClient.invalidateQueries({ queryKey: ['availability'] });
  };
}

export function useCreateService() {
  const invalidate = useCatalogueInvalidation();
  return useMutation({
    mutationFn: async (input: ServiceInput) => {
      const { data } = await apiClient.post<Service>('/api/services', input);
      return data;
    },
    onSuccess: invalidate,
  });
}

export function useUpdateService() {
  const invalidate = useCatalogueInvalidation();
  return useMutation({
    mutationFn: async ({ id, input }: { id: number; input: ServiceInput }) => {
      const { data } = await apiClient.put<Service>(`/api/services/${id}`, input);
      return data;
    },
    onSuccess: invalidate,
  });
}

export function useSetServiceActive() {
  const invalidate = useCatalogueInvalidation();
  return useMutation({
    mutationFn: async ({ id, active }: { id: number; active: boolean }) => {
      const { data } = await apiClient.patch<Service>(`/api/services/${id}/active`, null, {
        params: { value: active },
      });
      return data;
    },
    onSuccess: invalidate,
  });
}

export function useDeleteService() {
  const invalidate = useCatalogueInvalidation();
  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/api/services/${id}`);
    },
    onSuccess: invalidate,
  });
}
