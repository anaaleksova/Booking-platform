import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import type { DayInterval, Staff } from '@/types/api';

export interface StaffInput {
  name: string;
  email?: string;
  phone?: string;
  photoUrl?: string;
  active: boolean;
  serviceIds: number[];
}

export function useStaff(activeOnly = false) {
  return useQuery({
    queryKey: queryKeys.staff(activeOnly),
    queryFn: async () => {
      const { data } = await apiClient.get<Staff[]>('/api/staff', { params: { activeOnly } });
      return data;
    },
  });
}

function useStaffInvalidation() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['staff'] });
    void queryClient.invalidateQueries({ queryKey: ['availability'] });
  };
}

export function useCreateStaff() {
  const invalidate = useStaffInvalidation();
  return useMutation({
    mutationFn: async (input: StaffInput) => {
      const { data } = await apiClient.post<Staff>('/api/staff', input);
      return data;
    },
    onSuccess: invalidate,
  });
}

export function useUpdateStaff() {
  const invalidate = useStaffInvalidation();
  return useMutation({
    mutationFn: async ({ id, input }: { id: number; input: StaffInput }) => {
      const { data } = await apiClient.put<Staff>(`/api/staff/${id}`, input);
      return data;
    },
    onSuccess: invalidate,
  });
}

export function useDeleteStaff() {
  const invalidate = useStaffInvalidation();
  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/api/staff/${id}`);
    },
    onSuccess: invalidate,
  });
}

/** Replaces a staff member's whole week; an empty list means "business hours". */
export function useReplaceStaffWorkingHours() {
  const invalidate = useStaffInvalidation();
  return useMutation({
    mutationFn: async ({ id, intervals }: { id: number; intervals: DayInterval[] }) => {
      const { data } = await apiClient.put<DayInterval[]>(`/api/staff/${id}/working-hours`, intervals);
      return data;
    },
    onSuccess: invalidate,
  });
}
