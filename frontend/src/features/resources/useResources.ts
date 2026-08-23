import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import type { Resource } from '@/types/api';

export interface ResourceInput {
  name: string;
  description?: string;
  active: boolean;
  serviceIds: number[];
}

export function useResources(activeOnly = false) {
  return useQuery({
    queryKey: queryKeys.resources(activeOnly),
    queryFn: async () => {
      const { data } = await apiClient.get<Resource[]>('/api/resources', { params: { activeOnly } });
      return data;
    },
  });
}

function useResourceInvalidation() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['resources'] });
    void queryClient.invalidateQueries({ queryKey: ['availability'] });
  };
}

export function useCreateResource() {
  const invalidate = useResourceInvalidation();
  return useMutation({
    mutationFn: async (input: ResourceInput) => {
      const { data } = await apiClient.post<Resource>('/api/resources', input);
      return data;
    },
    onSuccess: invalidate,
  });
}

export function useUpdateResource() {
  const invalidate = useResourceInvalidation();
  return useMutation({
    mutationFn: async ({ id, input }: { id: number; input: ResourceInput }) => {
      const { data } = await apiClient.put<Resource>(`/api/resources/${id}`, input);
      return data;
    },
    onSuccess: invalidate,
  });
}

export function useDeleteResource() {
  const invalidate = useResourceInvalidation();
  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/api/resources/${id}`);
    },
    onSuccess: invalidate,
  });
}
