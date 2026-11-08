import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import { useInvalidatingMutation } from '@/features/mutation';
import type { PublicSite, SiteDocument, SiteState } from './types';

/** Publishing changes what every customer page looks like, and the brand colour on the business. */
const PUBLISH_AFFECTS = [queryKeys.site, queryKeys.business, ['public']];

export function useSite() {
  return useQuery({
    queryKey: queryKeys.site,
    queryFn: async () => (await apiClient.get<SiteState>('/api/admin/site')).data,
  });
}

export function useSaveSiteDraft() {
  return useInvalidatingMutation(
    async (input: { draftVersion: number; document: SiteDocument }) =>
      (await apiClient.put<SiteState>('/api/admin/site/draft', input)).data,
    [queryKeys.site],
  );
}

export function usePublishSite() {
  return useInvalidatingMutation(
    async (_: void) => (await apiClient.post<SiteState>('/api/admin/site/publish')).data,
    PUBLISH_AFFECTS,
  );
}

export function useDiscardSiteDraft() {
  return useInvalidatingMutation(
    async (_: void) => (await apiClient.post<SiteState>('/api/admin/site/discard')).data,
    [queryKeys.site],
  );
}

/** The published homepage: never the draft. */
export function usePublicSite(slug: string) {
  return useQuery({
    queryKey: queryKeys.publicSite(slug),
    queryFn: async () => (await apiClient.get<PublicSite>(`/api/public/businesses/${slug}/site`)).data,
  });
}

/** Where an uploaded image is served from; the document stores only its id. */
export function siteImageUrl(mediaId: string): string {
  return `/api/public/media/${mediaId}`;
}
