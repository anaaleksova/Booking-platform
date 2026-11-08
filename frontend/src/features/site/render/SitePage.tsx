import { DARK_HERO_LAYOUTS, isHidden } from '../catalog';
import type { SiteDocument } from '../types';
import { SectionRenderer } from './SectionRenderer';
import { SiteDataProvider, type SiteData } from './SiteData';

/**
 * A homepage drawn from its document: the sections in order, each from the
 * registry, all reading the same live data. The theme and the header and
 * footer around it come from `BusinessShell`.
 */
export function SitePage({ site, data }: { site: SiteDocument; data: SiteData }) {
  return (
    <SiteDataProvider value={data}>
      {site.sections.map((section) => (
        <SectionRenderer key={section.id} section={section} />
      ))}
    </SiteDataProvider>
  );
}

/**
 * Whether the header runs transparently over the page's top: only when the
 * owner wants it and the first section shown is a hero of white text over a
 * dark image. Over a light section it would be unreadable.
 */
export function headerOverHero(site: SiteDocument): boolean {
  const first = site.sections.find((section) => !isHidden(section));
  return site.header.transparentOverHero && first?.type === 'HERO' && DARK_HERO_LAYOUTS.includes(first.layout);
}
