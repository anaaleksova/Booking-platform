import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import type {
  DaySlots,
  PriceQuote,
  PublicBusiness,
  PublicReservation,
  PublicService,
  ResourceSelection,
} from '@/types/api';

export interface PublicReservationInput {
  serviceId: number;
  participants: number;
  startTime: string;
  resourceSelections: ResourceSelection[];
  discountCode?: string;
  customer: { firstName: string; lastName: string; email: string; phone: string };
  notes?: string;
}

export function usePublicBusiness(slug: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.publicBusiness(slug),
    queryFn: async () => (await apiClient.get<PublicBusiness>(`/api/public/businesses/${slug}`)).data,
    enabled: enabled && Boolean(slug),
  });
}

export function usePublicServices(slug: string) {
  return useQuery({
    queryKey: queryKeys.publicServices(slug),
    queryFn: async () => (await apiClient.get<PublicService[]>(`/api/public/businesses/${slug}/services`)).data,
  });
}

export function usePublicService(slug: string, serviceId: number) {
  return useQuery({
    queryKey: queryKeys.publicService(slug, serviceId),
    queryFn: async () =>
      (await apiClient.get<PublicService>(`/api/public/businesses/${slug}/services/${serviceId}`)).data,
  });
}

/**
 * Slots per day for a date range and group size, in one request, so the date
 * strip can grey out full days. Kept fresh: availability is what customers race for.
 */
export function usePublicAvailability(slug: string, serviceId: number, from: string, to: string, participants: number) {
  return useQuery({
    queryKey: queryKeys.publicAvailability(slug, { serviceId, from, to, participants }),
    queryFn: async () =>
      (
        await apiClient.get<DaySlots[]>(`/api/public/businesses/${slug}/services/${serviceId}/availability`, {
          params: { from, to, participants },
        })
      ).data,
    staleTime: 10_000,
    placeholderData: (previous) => previous,
  });
}

/** A few upcoming free times for the landing page's quick-book chips. */
export async function fetchQuote(slug: string, serviceId: number, participants: number, discountCode?: string) {
  return (
    await apiClient.post<PriceQuote>(`/api/public/businesses/${slug}/quote`, {
      serviceId,
      participants,
      discountCode: discountCode || undefined,
    })
  ).data;
}

export async function createPublicReservation(slug: string, input: PublicReservationInput) {
  return (await apiClient.post<PublicReservation>(`/api/public/businesses/${slug}/reservations`, input)).data;
}

export function usePublicReservation(reference: string) {
  return useQuery({
    queryKey: queryKeys.publicReservation(reference),
    queryFn: async () => (await apiClient.get<PublicReservation>(`/api/public/reservations/${reference}`)).data,
  });
}

export async function cancelPublicReservation(reference: string) {
  return (await apiClient.post<PublicReservation>(`/api/public/reservations/${reference}/cancel`, {})).data;
}
