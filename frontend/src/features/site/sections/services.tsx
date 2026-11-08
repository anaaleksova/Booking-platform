import { useMemo, type ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { alpha } from '@mui/material/styles';
import AccessTimeRoundedIcon from '@mui/icons-material/AccessTimeRounded';
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded';
import GroupRoundedIcon from '@mui/icons-material/GroupRounded';
import SpaRoundedIcon from '@mui/icons-material/SpaRounded';
import { EmptyState, ErrorState, LoadingState } from '@/components/states';
import { mediaUrl } from '@/features/media/api';
import { bookLabel, isFree } from '@/features/public/freeBooking';
import { Reveal } from '@/features/public/Reveal';
import type { PublicService } from '@/types/api';
import { formatDuration, formatMoney } from '@/utils/format';
import { useSiteData } from '../render/SiteData';
import { useSiteTheme } from '../render/SiteThemeScope';
import type { ServicesSection } from '../types';
import { Carousel, Meta, SectionBody, SectionHeading, textOr, useImageShape, useOnlyFree, useSectionServices, type SectionProps } from './shared';

/**
 * What every services layout shares: the heading, the loading and empty
 * states, and the booking button underneath. The layout draws the list.
 */
function ServicesFrame({ section, children }: { section: ServicesSection; children: (services: PublicService[]) => ReactNode }) {
  const { business, servicesLoading, servicesError } = useSiteData();
  const free = useOnlyFree();
  const services = useSectionServices(section.content.source, section.content.serviceIds);
  const { buttonStyle } = useSiteTheme().components;
  return (
    <SectionBody>
      <SectionHeading
        section={section}
        eyebrow={textOr(section.content.eyebrow, free ? 'Reservations' : 'Price list')}
        title={textOr(section.content.title, free ? 'What you can book' : 'Services & prices')}
      />
      {servicesLoading ? <LoadingState /> : null}
      {servicesError ? <ErrorState error={servicesError} /> : null}
      {!servicesLoading && !servicesError && services.length === 0 ? (
        <EmptyState title="No services are open for booking right now" />
      ) : null}
      {children(services)}
      {section.content.showBookButton ? (
        <Stack alignItems="center" sx={{ mt: 5 }}>
          <Button
            component={RouterLink}
            to={`/b/${business.slug}/book`}
            variant="contained"
            size="large"
            sx={{
              borderRadius: 'var(--site-radius-button, 999px)',
              px: 4,
              // Filled, it has always been in the text colour; outline and text-only buttons follow the theme.
              ...(buttonStyle === 'FILLED' ? { bgcolor: 'text.primary', color: 'background.default', '&:hover': { bgcolor: 'text.secondary' } } : {}),
            }}
          >
            {bookLabel(services)}
          </Button>
        </Stack>
      ) : null}
    </SectionBody>
  );
}

/** The price and "/ person", or nothing for a free reservation. */
function Price({ service, size = 'body1' }: { service: PublicService; size?: 'body1' | 'h6' | 'h5' }) {
  if (isFree(service)) {
    return null;
  }
  return (
    <Typography variant={size} fontWeight={800} sx={{ whiteSpace: 'nowrap' }}>
      {formatMoney(service.price, service.currency)}
      {service.pricePerParticipant ? (
        <Typography component="span" variant="caption" color="text.secondary">
          {' '}
          / person
        </Typography>
      ) : null}
    </Typography>
  );
}

function Details({ service }: { service: PublicService }) {
  return (
    <Stack direction="row" spacing={2} sx={{ mt: 0.5, color: 'text.secondary' }}>
      <Meta icon={<AccessTimeRoundedIcon sx={{ fontSize: 16 }} />} text={formatDuration(service.durationMinutes)} />
      {service.maxParticipants > 1 ? <Meta icon={<GroupRoundedIcon sx={{ fontSize: 16 }} />} text={`Up to ${service.maxParticipants}`} /> : null}
    </Stack>
  );
}

function bookingPath(slug: string, service: PublicService) {
  return `/b/${slug}/book?service=${service.id}`;
}

/** The service's photo, or the brand colour with an icon when it has none. */
function ServicePhoto({ service, sx }: { service: PublicService; sx?: object }) {
  return (
    <Box sx={{ overflow: 'hidden', bgcolor: (theme) => alpha(theme.palette.primary.main, 0.12), display: 'grid', placeItems: 'center', ...sx }}>
      {service.imageUrl ? (
        <Box component="img" className="service-img" src={mediaUrl(service.imageUrl)} alt="" loading="lazy" sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block', transition: 'transform 300ms ease' }} />
      ) : (
        <SpaRoundedIcon sx={{ fontSize: 40, color: 'primary.main', opacity: 0.5 }} />
      )}
    </Box>
  );
}

/** A card or row that is a link into the booking flow for its service, lifting on hover. */
const linkCard = {
  color: 'inherit',
  textDecoration: 'none',
  transition: 'box-shadow 200ms ease, transform 200ms ease',
  '&:hover, &:focus-visible': {
    boxShadow: '0 12px 32px rgba(17,18,20,0.12)',
    transform: 'translateY(-3px)',
    '& .service-img': { transform: 'scale(1.05)' },
  },
  '@media (prefers-reduced-motion: reduce)': { '&:hover, &:focus-visible': { transform: 'none' } },
} as const;

/** The price list by category the page has always had. */
export function ServicesGroupedList({ section }: SectionProps<ServicesSection>) {
  return <ServicesFrame section={section}>{(services) => <ServiceGroups services={services} />}</ServicesFrame>;
}

function ServiceGroups({ services }: { services: PublicService[] }) {
  const shape = useImageShape();
  const { business } = useSiteData();
  const groups = useMemo(() => {
    const map = new Map<string, PublicService[]>();
    services.forEach((service) => {
      const key = service.category?.trim() || (isFree(service) ? 'Reservations' : 'Services');
      map.set(key, [...(map.get(key) ?? []), service]);
    });
    return [...map.entries()];
  }, [services]);

  return (
    <Grid container spacing={3}>
      {groups.map(([category, items], groupIndex) => (
        <Grid item xs={12} md={groups.length > 1 ? 6 : 12} key={category}>
          <Reveal delay={groupIndex * 120} sx={{ height: '100%' }}>
            <Paper variant="outlined" sx={{ p: { xs: 2.5, md: 3.5 }, height: '100%', borderRadius: 'var(--site-radius-card, 56px)' }}>
              <Typography variant="h6" component="h3" fontWeight={800} sx={{ mb: 2 }}>
                {category}
              </Typography>
              <Stack spacing={1}>
                {items.map((service, index) => (
                  <Reveal key={service.id} delay={120 + index * 90}>
                    <Stack
                      component={RouterLink}
                      to={bookingPath(business.slug, service)}
                      direction="row"
                      spacing={2}
                      alignItems="center"
                      sx={{
                        color: 'inherit',
                        textDecoration: 'none',
                        px: { xs: 2, md: 2.5 },
                        py: { xs: 1.75, md: 2 },
                        mx: { xs: -1, md: -1.5 },
                        borderRadius: 3,
                        border: 1,
                        borderColor: 'transparent',
                        transition: 'background-color 200ms ease, border-color 200ms ease, box-shadow 200ms ease, transform 200ms ease',
                        '& .service-arrow': { opacity: 0, transform: 'translateX(-6px)', transition: 'opacity 200ms ease, transform 200ms ease' },
                        '& .service-img': { transition: 'transform 300ms ease' },
                        '&:hover, &:focus-visible': {
                          bgcolor: (theme) => alpha(theme.palette.primary.main, 0.05),
                          borderColor: (theme) => alpha(theme.palette.primary.main, 0.18),
                          boxShadow: '0 8px 24px rgba(17,18,20,0.06)',
                          transform: 'translateY(-2px)',
                          '& .service-arrow': { opacity: 1, transform: 'none' },
                          '& .service-img': { transform: 'scale(1.05)' },
                        },
                        '@media (prefers-reduced-motion: reduce)': { '&:hover, &:focus-visible': { transform: 'none' } },
                      }}
                    >
                      {service.imageUrl ? (
                        <Box sx={{ width: 72, height: 72, ...shape(35), overflow: 'hidden', flexShrink: 0 }}>
                          <Box component="img" className="service-img" src={mediaUrl(service.imageUrl)} alt="" loading="lazy" sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
                        </Box>
                      ) : null}
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Stack direction="row" alignItems="baseline" spacing={1}>
                          <Typography fontWeight={700} noWrap>
                            {service.name}
                          </Typography>
                          {isFree(service) ? null : (
                            <>
                              <Box sx={{ flex: 1, borderBottom: '1px dotted', borderColor: 'divider', minWidth: 16, transform: 'translateY(-4px)' }} />
                              <Price service={service} />
                            </>
                          )}
                        </Stack>
                        {service.description ? (
                          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                            {service.description}
                          </Typography>
                        ) : null}
                        <Details service={service} />
                      </Box>
                      <ArrowForwardRoundedIcon className="service-arrow" color="primary" sx={{ fontSize: 20, flexShrink: 0 }} />
                    </Stack>
                  </Reveal>
                ))}
              </Stack>
            </Paper>
          </Reveal>
        </Grid>
      ))}
    </Grid>
  );
}

/** One card per service, photo on top. */
function ServiceCard({ service }: { service: PublicService }) {
  const { business } = useSiteData();
  return (
    <Paper component={RouterLink} to={bookingPath(business.slug, service)} variant="outlined" sx={{ ...linkCard, display: 'flex', flexDirection: 'column', height: '100%', borderRadius: 'var(--site-radius-card, 56px)', overflow: 'hidden' }}>
      <ServicePhoto service={service} sx={{ aspectRatio: '4 / 3' }} />
      <Box sx={{ p: 2.5, flex: 1, display: 'flex', flexDirection: 'column' }}>
        <Typography variant="h6" component="h3" fontWeight={800}>
          {service.name}
        </Typography>
        {service.description ? (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
            {service.description}
          </Typography>
        ) : null}
        <Box sx={{ flex: 1 }} />
        <Stack direction="row" justifyContent="space-between" alignItems="flex-end" sx={{ mt: 2 }}>
          <Details service={service} />
          <Price service={service} />
        </Stack>
      </Box>
    </Paper>
  );
}

/** Cards in a grid: three across on wide screens. */
export function ServicesGrid({ section }: SectionProps<ServicesSection>) {
  return (
    <ServicesFrame section={section}>
      {(services) => (
        <Grid container spacing={3} justifyContent="center">
          {services.map((service, index) => (
            <Grid item xs={12} sm={6} md={4} key={service.id}>
              <Reveal delay={(index % 3) * 100} sx={{ height: '100%' }}>
                <ServiceCard service={service} />
              </Reveal>
            </Grid>
          ))}
        </Grid>
      )}
    </ServicesFrame>
  );
}

/** The same cards in a row that scrolls sideways. */
export function ServicesCarousel({ section }: SectionProps<ServicesSection>) {
  return (
    <ServicesFrame section={section}>
      {(services) =>
        services.length ? (
          <Carousel label="Services" itemWidth={{ xs: '78%', md: 'calc((100% - 24px) / 3)' }}>
            {services.map((service) => (
              <ServiceCard key={service.id} service={service} />
            ))}
          </Carousel>
        ) : null
      }
    </ServicesFrame>
  );
}

/** One plain row per service, no categories: name, details and price. */
export function ServicesList({ section }: SectionProps<ServicesSection>) {
  const { business } = useSiteData();
  return (
    <ServicesFrame section={section}>
      {(services) => (
        <Paper variant="outlined" sx={{ borderRadius: 'var(--site-radius-card, 56px)', overflow: 'hidden', maxWidth: 820, mx: 'auto' }}>
          {services.map((service, index) => (
            <Stack
              key={service.id}
              component={RouterLink}
              to={bookingPath(business.slug, service)}
              direction="row"
              spacing={2}
              alignItems="center"
              sx={{
                color: 'inherit',
                textDecoration: 'none',
                px: { xs: 2.5, md: 3.5 },
                py: 2.25,
                borderTop: index ? 1 : 0,
                borderColor: 'divider',
                transition: 'background-color 200ms ease',
                '&:hover, &:focus-visible': { bgcolor: (theme) => alpha(theme.palette.primary.main, 0.05) },
              }}
            >
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography fontWeight={700}>{service.name}</Typography>
                {service.description ? (
                  <Typography variant="body2" color="text.secondary" noWrap>
                    {service.description}
                  </Typography>
                ) : null}
                <Details service={service} />
              </Box>
              <Price service={service} />
              <ArrowForwardRoundedIcon color="primary" sx={{ fontSize: 20 }} />
            </Stack>
          ))}
        </Paper>
      )}
    </ServicesFrame>
  );
}

/** Big photo and text side by side, swapping sides service by service. */
export function ServicesAlternating({ section }: SectionProps<ServicesSection>) {
  const shape = useImageShape();
  const { business } = useSiteData();
  return (
    <ServicesFrame section={section}>
      {(services) => (
        <Stack spacing={{ xs: 5, md: 8 }}>
          {services.map((service, index) => (
            <Reveal key={service.id}>
              <Grid container spacing={{ xs: 2.5, md: 6 }} alignItems="center" direction={index % 2 ? 'row-reverse' : 'row'}>
                <Grid item xs={12} md={6}>
                  <ServicePhoto service={service} sx={{ aspectRatio: '3 / 2', ...shape(56) }} />
                </Grid>
                <Grid item xs={12} md={6}>
                  <Typography variant="h4" component="h3" fontWeight={800}>
                    {service.name}
                  </Typography>
                  {service.description ? (
                    <Typography color="text.secondary" sx={{ mt: 1.5, fontSize: 17, lineHeight: 1.7 }}>
                      {service.description}
                    </Typography>
                  ) : null}
                  <Stack direction="row" spacing={3} alignItems="center" sx={{ mt: 2.5 }}>
                    <Price service={service} size="h5" />
                    <Details service={service} />
                  </Stack>
                  <Button component={RouterLink} to={bookingPath(business.slug, service)} variant="outlined" endIcon={<ArrowForwardRoundedIcon />} sx={{ mt: 3, borderRadius: 'var(--site-radius-button, 999px)', px: 3 }}>
                    Book this
                  </Button>
                </Grid>
              </Grid>
            </Reveal>
          ))}
        </Stack>
      )}
    </ServicesFrame>
  );
}
