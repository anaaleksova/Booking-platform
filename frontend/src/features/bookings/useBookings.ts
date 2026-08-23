import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import type { Booking, BookingStatus, DashboardStats } from '@/types/api';

export interface BookingFilters {
  from?: string;
  to?: string;
  staffId?: number;
  customerId?: number;
  status?: BookingStatus;
}

export interface CreateBookingInput {
  serviceId: number;
  staffId?: number | null;
  resourceId?: number | null;
  customerId?: number | null;
  customer?: { name: string; phone?: string; email?: string };
  /** Wall-clock in the business timezone: `2026-08-20T10:00:00`. */
  startDateTime: string;
  customerNotes?: string;
  internalNotes?: string;
}

export function useBookings(filters: BookingFilters, enabled = true) {
  return useQuery({
    queryKey: queryKeys.bookings(filters),
    enabled,
    queryFn: async () => {
      const { data } = await apiClient.get<Booking[]>('/api/bookings', { params: filters });
      return data;
    },
  });
}

export function useDashboardStats() {
  return useQuery({
    queryKey: queryKeys.dashboardStats,
    queryFn: async () => {
      const { data } = await apiClient.get<DashboardStats>('/api/dashboard/stats');
      return data;
    },
  });
}

/**
 * Anything that writes a booking invalidates bookings, availability and the
 * dashboard counters — a newly taken slot must disappear from the picker
 * immediately.
 */
function useBookingInvalidation() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['bookings'] });
    void queryClient.invalidateQueries({ queryKey: ['availability'] });
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    void queryClient.invalidateQueries({ queryKey: ['customers'] });
  };
}

export function useCreateBooking() {
  const invalidate = useBookingInvalidation();
  return useMutation({
    mutationFn: async (input: CreateBookingInput) => {
      const { data } = await apiClient.post<Booking>('/api/bookings', input);
      return data;
    },
    onSuccess: invalidate,
  });
}

export function useRescheduleBooking() {
  const invalidate = useBookingInvalidation();
  return useMutation({
    mutationFn: async ({
      id,
      startDateTime,
      staffId,
      resourceId,
    }: {
      id: number;
      startDateTime: string;
      staffId?: number | null;
      resourceId?: number | null;
    }) => {
      const { data } = await apiClient.put<Booking>(`/api/bookings/${id}/reschedule`, {
        startDateTime,
        staffId,
        resourceId,
      });
      return data;
    },
    onSuccess: invalidate,
  });
}

export function useCancelBooking() {
  const invalidate = useBookingInvalidation();
  return useMutation({
    mutationFn: async ({ id, reason }: { id: number; reason?: string }) => {
      const { data } = await apiClient.post<Booking>(`/api/bookings/${id}/cancel`, { reason });
      return data;
    },
    onSuccess: invalidate,
  });
}

export function useChangeBookingOutcome() {
  const invalidate = useBookingInvalidation();
  return useMutation({
    mutationFn: async ({ id, outcome }: { id: number; outcome: 'complete' | 'no-show' }) => {
      const { data } = await apiClient.post<Booking>(`/api/bookings/${id}/${outcome}`);
      return data;
    },
    onSuccess: invalidate,
  });
}
