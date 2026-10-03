import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import { useInvalidatingMutation } from '@/features/mutation';
import type { DaySlots, PriceQuote, Reservation, ReservationStatus, ResourceSelection, Slot } from '@/types/api';

export interface ReservationFilters {
  from?: string;
  to?: string;
  status?: ReservationStatus;
  serviceId?: number;
  customerId?: number;
  resourceId?: number;
}

export interface AdminReservationInput {
  serviceId: number;
  participants: number;
  startTime: string;
  resourceSelections?: ResourceSelection[];
  discountCode?: string;
  customerId?: number;
  customer?: { firstName: string; lastName?: string; email?: string; phone?: string };
  customerNotes?: string;
  internalNotes?: string;
}

/** Everything a reservation change can affect. */
const AFFECTED = [['reservations'], ['analytics'], ['customers'], ['discounts']];

export function useReservations(filters: ReservationFilters) {
  return useQuery({
    queryKey: queryKeys.reservations(filters),
    queryFn: async () => (await apiClient.get<Reservation[]>('/api/admin/reservations', { params: filters })).data,
  });
}

export function useReservation(id: number | null) {
  return useQuery({
    queryKey: queryKeys.reservation(id ?? 0),
    queryFn: async () => (await apiClient.get<Reservation>(`/api/admin/reservations/${id}`)).data,
    enabled: id !== null,
  });
}

/** What else is free at the reservation's time — for changing its resources. */
export function useReservationOptions(id: number, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.reservationOptions(id),
    queryFn: async () => (await apiClient.get<Slot>(`/api/admin/reservations/${id}/resource-options`)).data,
    enabled,
    staleTime: 0,
  });
}

/** Free slots for the admin; `excludeReservationId` lets a reservation being moved ignore itself. */
export function useAdminAvailability(
  serviceId: number | undefined,
  date: string | undefined,
  participants: number,
  excludeReservationId?: number,
) {
  return useQuery({
    queryKey: queryKeys.adminAvailability({ serviceId, date, participants, excludeReservationId }),
    queryFn: async () =>
      (
        await apiClient.get<DaySlots[]>('/api/admin/availability', {
          params: { serviceId, from: date, participants, excludeReservationId },
        })
      ).data[0],
    enabled: serviceId !== undefined && Boolean(date) && participants > 0,
    staleTime: 0,
  });
}

export function useCreateReservation() {
  return useInvalidatingMutation(
    async (input: AdminReservationInput) => (await apiClient.post<Reservation>('/api/admin/reservations', input)).data,
    AFFECTED,
  );
}

export function useChangeReservationStatus() {
  return useInvalidatingMutation(
    async ({ id, status, reason }: { id: number; status: ReservationStatus; reason?: string }) =>
      (await apiClient.patch<Reservation>(`/api/admin/reservations/${id}`, { status, reason })).data,
    AFFECTED,
  );
}

export function useRescheduleReservation() {
  return useInvalidatingMutation(
    async ({
      id,
      startTime,
      resourceSelections,
    }: {
      id: number;
      startTime: string;
      resourceSelections?: ResourceSelection[];
    }) =>
      (await apiClient.put<Reservation>(`/api/admin/reservations/${id}/schedule`, { startTime, resourceSelections }))
        .data,
    AFFECTED,
  );
}

export async function fetchAdminQuote(serviceId: number, participants: number, discountCode?: string) {
  return (
    await apiClient.post<PriceQuote>('/api/admin/pricing/quote', {
      serviceId,
      participants,
      discountCode: discountCode || undefined,
    })
  ).data;
}
