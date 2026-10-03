import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import { useInvalidatingMutation } from '@/features/mutation';
import type { BlockedTime, Business, BusinessSettings, DayInterval, GalleryImage } from '@/types/api';

export type BusinessInput = Pick<
  Business,
  | 'name'
  | 'slug'
  | 'description'
  | 'tagline'
  | 'accentColor'
  | 'logoMediaId'
  | 'coverMediaId'
  | 'instagramUrl'
  | 'facebookUrl'
  | 'websiteUrl'
  | 'showMap'
  | 'showGallery'
  | 'phone'
  | 'email'
  | 'address'
  | 'currency'
  | 'timezone'
>;

/** Builds a complete update from the current business, so a partial change keeps everything else. */
export function toBusinessInput(business: Business, patch: Partial<BusinessInput> = {}): BusinessInput {
  return {
    name: business.name,
    slug: business.slug,
    description: business.description ?? '',
    tagline: business.tagline ?? '',
    accentColor: business.accentColor,
    logoMediaId: business.logoMediaId ?? null,
    coverMediaId: business.coverMediaId ?? null,
    instagramUrl: business.instagramUrl ?? '',
    facebookUrl: business.facebookUrl ?? '',
    websiteUrl: business.websiteUrl ?? '',
    showMap: business.showMap,
    showGallery: business.showGallery,
    phone: business.phone ?? '',
    email: business.email ?? '',
    address: business.address ?? '',
    currency: business.currency,
    timezone: business.timezone,
    ...patch,
  };
}

export interface BlockedTimeInput {
  resourceId: number | null;
  startTime: string;
  endTime: string;
  reason?: string;
}

export function useBusiness() {
  return useQuery({
    queryKey: queryKeys.business,
    queryFn: async () => (await apiClient.get<Business>('/api/admin/business')).data,
  });
}

export function useUpdateBusiness() {
  return useInvalidatingMutation(
    async (input: BusinessInput) => (await apiClient.put<Business>('/api/admin/business', input)).data,
    [queryKeys.business, ['analytics'], ['reservations'], ['public']],
  );
}

export function useBusinessSettings() {
  return useQuery({
    queryKey: queryKeys.businessSettings,
    queryFn: async () => (await apiClient.get<BusinessSettings>('/api/admin/business/settings')).data,
  });
}

export function useUpdateBusinessSettings() {
  return useInvalidatingMutation(
    async (input: BusinessSettings) =>
      (await apiClient.put<BusinessSettings>('/api/admin/business/settings', input)).data,
    [queryKeys.businessSettings],
  );
}

export function useOpeningHours() {
  return useQuery({
    queryKey: queryKeys.openingHours,
    queryFn: async () => (await apiClient.get<DayInterval[]>('/api/admin/opening-hours')).data,
  });
}

export function useReplaceOpeningHours() {
  return useInvalidatingMutation(
    async (intervals: DayInterval[]) =>
      (await apiClient.put<DayInterval[]>('/api/admin/opening-hours', intervals)).data,
    [queryKeys.openingHours],
  );
}

export function useBlockedTimes() {
  return useQuery({
    queryKey: queryKeys.blockedTimes,
    queryFn: async () => (await apiClient.get<BlockedTime[]>('/api/admin/blocked-times')).data,
  });
}

export function useCreateBlockedTime() {
  return useInvalidatingMutation(
    async (input: BlockedTimeInput) => (await apiClient.post<BlockedTime>('/api/admin/blocked-times', input)).data,
    [queryKeys.blockedTimes],
  );
}

export function useDeleteBlockedTime() {
  return useInvalidatingMutation(
    async (id: number) => {
      await apiClient.delete(`/api/admin/blocked-times/${id}`);
    },
    [queryKeys.blockedTimes],
  );
}

// --- gallery -------------------------------------------------------------------

/** Gallery changes are saved immediately, and refresh the public page's cache too. */
const GALLERY_AFFECTS = [queryKeys.gallery, ['public']];

export function useGallery() {
  return useQuery({
    queryKey: queryKeys.gallery,
    queryFn: async () => (await apiClient.get<GalleryImage[]>('/api/admin/gallery')).data,
  });
}

export function useAddGalleryImage() {
  return useInvalidatingMutation(
    async (input: { mediaId: string; caption?: string }) =>
      (await apiClient.post<GalleryImage>('/api/admin/gallery', input)).data,
    GALLERY_AFFECTS,
  );
}

export function useUpdateGalleryCaption() {
  return useInvalidatingMutation(
    async ({ id, caption }: { id: number; caption: string }) =>
      (await apiClient.put<GalleryImage>(`/api/admin/gallery/${id}`, { caption })).data,
    GALLERY_AFFECTS,
  );
}

export function useReorderGallery() {
  return useInvalidatingMutation(
    async (ids: number[]) => (await apiClient.put<GalleryImage[]>('/api/admin/gallery/order', ids)).data,
    GALLERY_AFFECTS,
  );
}

export function useDeleteGalleryImage() {
  return useInvalidatingMutation(
    async (id: number) => {
      await apiClient.delete(`/api/admin/gallery/${id}`);
    },
    GALLERY_AFFECTS,
  );
}
