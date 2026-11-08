import { useParams } from 'react-router-dom';
import Container from '@mui/material/Container';
import { ErrorState, LoadingState } from '@/components/states';
import { usePublicBusiness, usePublicEvents, usePublicServices } from '@/features/public/api';
import { BusinessShell } from '@/features/public/BusinessShell';
import { usePublicSite } from '@/features/site/api';
import { SitePage, headerOverHero } from '@/features/site/render/SitePage';

/**
 * A business's own homepage, as the business built it: its published
 * sections, in its theme, with "Book" one tap away everywhere.
 */
export function BusinessPage() {
  const slug = useParams().slug!;
  const business = usePublicBusiness(slug);
  const site = usePublicSite(slug);
  const services = usePublicServices(slug);
  const events = usePublicEvents(slug);

  if (business.isLoading || site.isLoading) {
    return <LoadingState />;
  }
  if (business.isError || !business.data || site.isError || !site.data) {
    return (
      <BusinessShell business={business.data}>
        <Container maxWidth="sm" sx={{ py: 8 }}>
          <ErrorState error={business.error ?? site.error} />
        </Container>
      </BusinessShell>
    );
  }
  const document = site.data.site;

  return (
    <BusinessShell business={business.data} transparentHeader={headerOverHero(document)} stickyHeader={document.header.sticky}>
      <SitePage
        site={document}
        data={{
          business: business.data,
          services: services.data ?? [],
          servicesLoading: services.isLoading,
          servicesError: services.isError ? services.error : null,
          events: events.data ?? [],
          team: site.data.team,
          gallery: site.data.gallery,
        }}
      />
    </BusinessShell>
  );
}
