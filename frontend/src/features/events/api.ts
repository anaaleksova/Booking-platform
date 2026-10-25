import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import { useInvalidatingMutation } from '@/features/mutation';
import type { BusinessEvent, EventInput } from '@/types/api';

export function useEvents() {
  return useQuery({
    queryKey: queryKeys.events,
    queryFn: async () => (await apiClient.get<BusinessEvent[]>('/api/admin/events')).data,
  });
}

export function useSaveEvent() {
  return useInvalidatingMutation(
    async ({ id, input }: { id?: number; input: EventInput }) =>
      id === undefined
        ? (await apiClient.post<BusinessEvent>('/api/admin/events', input)).data
        : (await apiClient.put<BusinessEvent>(`/api/admin/events/${id}`, input)).data,
    [queryKeys.events],
  );
}

export function useDeleteEvent() {
  return useInvalidatingMutation(
    async (id: number) => {
      await apiClient.delete(`/api/admin/events/${id}`);
    },
    [queryKeys.events],
  );
}
