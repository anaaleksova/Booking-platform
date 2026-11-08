import { useEffect, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { useEmployees } from '@/features/catalog/api';
import { useGallery } from '@/features/business/api';
import { usePublicBusiness, usePublicEvents, usePublicServices } from '@/features/public/api';
import { LoadingState } from '@/components/states';
import type { PublicBusiness } from '@/types/api';
import { SitePage } from '../render/SitePage';
import { SiteThemeScope } from '../render/SiteThemeScope';
import { completeTheme } from '../theme';
import type { SiteDocument } from '../types';

/** The width the preview is drawn at before it is scaled down: a laptop screen. */
const PAGE_WIDTH = 1280;

/**
 * The draft page as customers will see it on a computer, drawn at full width
 * and scaled down to fit, updating with every edit before anything is saved.
 * It cannot be clicked: links would leave the editor.
 *
 * @param business the business's details as the form above holds them, saved or not
 */
export function LivePreview({
  slug,
  document,
  business: edits,
}: {
  slug: string;
  document: SiteDocument;
  business: Partial<Pick<PublicBusiness, 'name' | 'tagline' | 'description' | 'logoUrl' | 'coverUrl'>>;
}) {
  const business = usePublicBusiness(slug);
  const services = usePublicServices(slug);
  const events = usePublicEvents(slug);
  const gallery = useGallery();
  const employees = useEmployees();

  const frame = useRef<HTMLDivElement>(null);
  const page = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(0.35);
  const [height, setHeight] = useState(0);

  useEffect(() => {
    if (typeof ResizeObserver === 'undefined') {
      return undefined;
    }
    const observer = new ResizeObserver(() => {
      if (frame.current) {
        setScale(frame.current.clientWidth / PAGE_WIDTH);
      }
      if (page.current) {
        setHeight(page.current.offsetHeight);
      }
    });
    if (frame.current) {
      observer.observe(frame.current);
    }
    if (page.current) {
      observer.observe(page.current);
    }
    return () => observer.disconnect();
  }, [business.data]);

  // `inert` keeps the preview out of the tab order and unclickable; React 18 does not know the attribute.
  useEffect(() => {
    page.current?.setAttribute('inert', '');
  }, [business.data]);

  return (
    <Box>
      <Typography variant="overline" color="text.secondary">
        Preview of your draft
      </Typography>
      <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
        <Box ref={frame} sx={{ height: '70vh', overflowY: 'auto', overflowX: 'hidden', position: 'relative' }}>
          {business.data ? (
            <Box sx={{ height: height * scale, position: 'relative' }}>
              <Box ref={page} aria-hidden sx={{ width: PAGE_WIDTH, transform: `scale(${scale})`, transformOrigin: 'top left', position: 'absolute', top: 0, left: 0 }}>
                <SiteThemeScope theme={completeTheme(document.theme)}>
                  <SitePage
                    site={document}
                    data={{
                      business: { ...business.data, ...edits },
                      services: services.data ?? [],
                      servicesLoading: services.isLoading,
                      servicesError: services.isError ? services.error : null,
                      events: events.data ?? [],
                      team: (employees.data ?? []).filter((employee) => employee.active)
                        .map((employee) => ({ id: employee.id, name: employee.name, imageUrl: employee.imageUrl })),
                      gallery: (gallery.data ?? []).map((photo) => ({ url: photo.url, caption: photo.caption })),
                    }}
                  />
                </SiteThemeScope>
              </Box>
            </Box>
          ) : (
            <LoadingState />
          )}
        </Box>
        <Box sx={{ p: 1.5, borderTop: 1, borderColor: 'divider' }}>
          <Typography variant="caption" color="text.secondary">
            How your page will look on a computer. Customers see it after you publish.
          </Typography>
        </Box>
      </Paper>
    </Box>
  );
}
