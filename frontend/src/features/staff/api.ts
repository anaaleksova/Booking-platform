import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { useInvalidatingMutation } from '@/features/mutation';
import type { AuthResponse } from '@/types/api';

/** An employee's login access, as the owner sees it. */
export interface StaffAccess {
  employeeId: number;
  state: 'NONE' | 'INVITED' | 'EXPIRED' | 'ACTIVE';
  email?: string | null;
  invitedAt?: string | null;
  expiresAt?: string | null;
}

/** What the invitation page shows before anything is decided. */
export interface InvitationInfo {
  businessName: string;
  employeeName: string;
  email: string;
  /** The address already has an account: sign in to accept. */
  accountExists: boolean;
}

const STAFF = [['staff']];

export function useStaffAccess() {
  return useQuery({
    queryKey: ['staff'],
    queryFn: async () => (await apiClient.get<StaffAccess[]>('/api/admin/staff')).data,
  });
}

export function useInviteEmployee() {
  return useInvalidatingMutation(
    async (employeeId: number) => (await apiClient.post<StaffAccess>(`/api/admin/employees/${employeeId}/invite`)).data,
    STAFF,
  );
}

export function useCancelInvitation() {
  return useInvalidatingMutation(async (employeeId: number) => {
    await apiClient.delete(`/api/admin/employees/${employeeId}/invite`);
  }, STAFF);
}

export function useRemoveAccess() {
  return useInvalidatingMutation(async (employeeId: number) => {
    await apiClient.delete(`/api/admin/employees/${employeeId}/access`);
  }, STAFF);
}

export function useInvitation(token: string) {
  return useQuery({
    queryKey: ['invitation', token],
    queryFn: async () => (await apiClient.get<InvitationInfo>(`/api/auth/invitations/${token}`)).data,
    retry: false,
  });
}

export async function acceptWithNewAccount(token: string, fullName: string, password: string) {
  return (await apiClient.post<AuthResponse>(`/api/auth/invitations/${token}/accept-new`, { fullName, password })).data;
}

export async function acceptAsSignedIn(token: string) {
  return (await apiClient.post<AuthResponse>(`/api/auth/invitations/${token}/accept`)).data;
}
