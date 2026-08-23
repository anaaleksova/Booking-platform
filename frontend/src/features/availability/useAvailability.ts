import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import type { BlockedTime, DayAvailability } from '@/types/api';

export interface AvailabilityFilters {
  serviceId?: number;
  from?: string;
  to?: string;
  staffId?: number | null;
  resourceId?: number | null;
  /** Set while rescheduling so the booking does not block its own new time. */
  excludeBookingId?: number;
}

/**
 * Dashboard availability. Lead-time rules are not applied by this endpoint —
 * the business may fill a slot sooner than customers are allowed to book.
 */
export function useAvailability(filters: AvailabilityFilters) {
  const enabled = Boolean(filters.serviceId && filters.from);
  return useQuery({
    queryKey: queryKeys.availability(filters),
    enabled,
    queryFn: async () => {
      const { data } = await apiClient.get<DayAvailability[]>('/api/availability', {
        params: {
          serviceId: filters.serviceId,
          from: filters.from,
          to: filters.to,
          staffId: filters.staffId ?? undefined,
          resourceId: filters.resourceId ?? undefined,
          excludeBookingId: filters.excludeBookingId,
        },
      });
      return data;
    },
    // Slots go stale the moment someone else books: never serve them from cache.
    staleTime: 0,
  });
}

export function useBlockedTimes(from?: string, to?: string) {
  return useQuery({
    queryKey: queryKeys.blockedTimes(from, to),
    queryFn: async () => {
      const { data } = await apiClient.get<BlockedTime[]>('/api/blocked-times', { params: { from, to } });
      return data;
    },
  });
}

export interface BlockedTimeInput {
  staffId?: number | null;
  resourceId?: number | null;
  startDateTime: string;
  endDateTime: string;
  reason?: string;
}

function useBlockedTimeInvalidation() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['blocked-times'] });
    void queryClient.invalidateQueries({ queryKey: ['availability'] });
  };
}

export function useCreateBlockedTime() {
  const invalidate = useBlockedTimeInvalidation();
  return useMutation({
    mutationFn: async (input: BlockedTimeInput) => {
      const { data } = await apiClient.post<BlockedTime>('/api/blocked-times', input);
      return data;
    },
    onSuccess: invalidate,
  });
}

export function useDeleteBlockedTime() {
  const invalidate = useBlockedTimeInvalidation();
  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/api/blocked-times/${id}`);
    },
    onSuccess: invalidate,
  });
}
