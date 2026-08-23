import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import type {
  DayAvailability,
  PublicBooking,
  PublicBusiness,
  PublicService,
  PublicStaff,
} from '@/types/api';

/**
 * The customer-facing API. No token is ever attached to these calls — the
 * business is identified by its slug and a booking by its unguessable
 * reference.
 */

export function usePublicBusiness(slug: string) {
  return useQuery({
    queryKey: queryKeys.publicBusiness(slug),
    queryFn: async () => {
      const { data } = await apiClient.get<PublicBusiness>(`/api/public/${slug}`);
      return data;
    },
    retry: false,
  });
}

export function usePublicServices(slug: string) {
  return useQuery({
    queryKey: queryKeys.publicServices(slug),
    queryFn: async () => {
      const { data } = await apiClient.get<PublicService[]>(`/api/public/${slug}/services`);
      return data;
    },
  });
}

export function usePublicStaff(slug: string, serviceId: number | null) {
  return useQuery({
    queryKey: queryKeys.publicStaff(slug, serviceId ?? 0),
    enabled: serviceId !== null,
    queryFn: async () => {
      const { data } = await apiClient.get<PublicStaff[]>(
        `/api/public/${slug}/services/${serviceId}/staff`,
      );
      return data;
    },
  });
}

export function usePublicAvailability(
  slug: string,
  filters: { serviceId: number | null; from: string; to?: string; staffId?: number | null },
) {
  return useQuery({
    queryKey: queryKeys.publicAvailability(slug, filters),
    enabled: filters.serviceId !== null,
    staleTime: 0,
    queryFn: async () => {
      const { data } = await apiClient.get<DayAvailability[]>(`/api/public/${slug}/availability`, {
        params: {
          serviceId: filters.serviceId,
          from: filters.from,
          to: filters.to ?? filters.from,
          staffId: filters.staffId ?? undefined,
        },
      });
      return data;
    },
  });
}

export interface PublicBookingInput {
  serviceId: number;
  staffId?: number | null;
  /** Wall-clock in the business timezone, e.g. `2026-08-20T10:00:00`. */
  startDateTime: string;
  name: string;
  phone?: string;
  email?: string;
  notes?: string;
}

export function useCreatePublicBooking(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: PublicBookingInput) => {
      const { data } = await apiClient.post<PublicBooking>(`/api/public/${slug}/bookings`, input);
      return data;
    },
    onSuccess: () => {
      // The slot just taken must vanish for anyone else on the page.
      void queryClient.invalidateQueries({ queryKey: ['public', slug, 'availability'] });
    },
  });
}

export function usePublicBookingByReference(reference: string) {
  return useQuery({
    queryKey: queryKeys.publicBooking(reference),
    queryFn: async () => {
      const { data } = await apiClient.get<PublicBooking>(`/api/public/bookings/${reference}`);
      return data;
    },
    retry: false,
  });
}

export function useCancelPublicBooking(reference: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (reason?: string) => {
      const { data } = await apiClient.post<PublicBooking>(`/api/public/bookings/${reference}/cancel`, {
        reason,
      });
      return data;
    },
    onSuccess: (booking) => {
      queryClient.setQueryData(queryKeys.publicBooking(reference), booking);
    },
  });
}
