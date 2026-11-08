import { createContext, useContext, type ReactNode } from 'react';
import type { PublicBusiness, PublicEvent, PublicService } from '@/types/api';
import type { PublicTeamMember } from '../types';

/**
 * The live data sections show: the business itself, its services, events,
 * team and gallery. Loaded once for the page and read by every section, so a
 * section only says which of them it wants.
 */
export interface SiteData {
  business: PublicBusiness;
  services: PublicService[];
  servicesLoading: boolean;
  servicesError: unknown;
  events: PublicEvent[];
  team: PublicTeamMember[];
  gallery: Array<{ url: string; caption?: string | null }>;
}

const SiteDataContext = createContext<SiteData | null>(null);

export function SiteDataProvider({ value, children }: { value: SiteData; children: ReactNode }) {
  return <SiteDataContext.Provider value={value}>{children}</SiteDataContext.Provider>;
}

export function useSiteData(): SiteData {
  const data = useContext(SiteDataContext);
  if (!data) {
    throw new Error('useSiteData must be used inside a SiteDataProvider');
  }
  return data;
}
