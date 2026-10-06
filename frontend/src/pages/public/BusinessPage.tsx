import { useMemo } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Container from '@mui/material/Container';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { alpha, keyframes } from '@mui/material/styles';
import AccessTimeRoundedIcon from '@mui/icons-material/AccessTimeRounded';
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded';
import CalendarMonthRoundedIcon from '@mui/icons-material/CalendarMonthRounded';
import GroupRoundedIcon from '@mui/icons-material/GroupRounded';
import MapRoundedIcon from '@mui/icons-material/MapRounded';
import PhoneRoundedIcon from '@mui/icons-material/PhoneRounded';
import PlaceRoundedIcon from '@mui/icons-material/PlaceRounded';
import { EmptyState, ErrorState, LoadingState } from '@/components/states';
import { mediaUrl } from '@/features/media/api';
import { usePublicBusiness, usePublicServices } from '@/features/public/api';
import { BusinessShell } from '@/features/public/BusinessShell';
import { GallerySection } from '@/features/public/GallerySection';
import { Reveal } from '@/features/public/Reveal';
import type { DayInterval, PublicBusiness, PublicService } from '@/types/api';
import { DAY_NAMES, formatDuration, formatInterval, formatMoney } from '@/utils/format';

/**
 * A business's own booking page: who they are, the price list and where
 * to find them — with "Book" one tap away everywhere.
 */
export function BusinessPage() {
  const slug = useParams().slug!;
  const business = usePublicBusiness(slug);
  const services = usePublicServices(slug);

  if (business.isLoading) {
    return <LoadingState />;
  }
  if (business.isError || !business.data) {
    return (
      <BusinessShell>
        <Container maxWidth="sm" sx={{ py: 8 }}>
          <ErrorState error={business.error} />
        </Container>
      </BusinessShell>
    );
  }
  const info = business.data;

  return (
    <BusinessShell business={info} transparentHeader>
      <Hero business={info} />
      <Box id="services" sx={{ py: { xs: 6, md: 10 }, bgcolor: 'background.paper' }}>
        <Container maxWidth="lg">
          <SectionTitle eyebrow="Price list" title="Services & prices" />
          {services.isLoading ? <LoadingState /> : null}
          {services.isError ? <ErrorState error={services.error} /> : null}
          {services.data?.length === 0 ? <EmptyState title="No services are open for booking right now" /> : null}
          <ServiceGroups slug={slug} services={services.data ?? []} />
          <Stack alignItems="center" sx={{ mt: 5 }}>
            <Button component={RouterLink} to={`/b/${slug}/book`} variant="contained" size="large" sx={{ borderRadius: 999, px: 4, bgcolor: 'text.primary', color: 'background.default', '&:hover': { bgcolor: 'text.secondary' } }}>
              Book an appointment
            </Button>
          </Stack>
        </Container>
      </Box>
      <GallerySection
        photos={info.gallery ?? []}
        title={(eyebrow, heading) => <SectionTitle eyebrow={eyebrow} title={heading} />}
      />
      {info.description ? (
        <Box sx={{ py: { xs: 6, md: 8 }, bgcolor: (info.gallery ?? []).length ? 'background.paper' : undefined }}>
          <Container maxWidth="md" sx={{ textAlign: 'center' }}>
            <SectionTitle eyebrow="About us" title={`Welcome to ${info.name}`} />
            <Reveal delay={120}>
              <Typography color="text.secondary" sx={{ fontSize: 18, lineHeight: 1.7, whiteSpace: 'pre-line' }}>
                {info.description}
              </Typography>
            </Reveal>
          </Container>
        </Box>
      ) : null}
      <FindUs business={info} />
    </BusinessShell>
  );
}

const riseIn = keyframes`
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: none; }
`;

const slowZoom = keyframes`
  from { transform: scale(1.12); }
  to { transform: scale(1); }
`;

const gradientDrift = keyframes`
  0%, 100% { background-position: 0% 0%; }
  50% { background-position: 100% 60%; }
`;

const softPulse = keyframes`
  0%, 100% { box-shadow: 0 10px 30px rgba(0,0,0,0.25), 0 0 0 0 rgba(255,255,255,0.35); }
  50% { box-shadow: 0 10px 30px rgba(0,0,0,0.25), 0 0 0 10px rgba(255,255,255,0); }
`;

/** Staggered entrance for the hero's lines; `step` is the order. */
const heroLine = (step: number) => ({
  opacity: 0,
  animation: `${riseIn} 800ms cubic-bezier(0.22, 1, 0.36, 1) ${150 + step * 140}ms forwards`,
  '@media (prefers-reduced-motion: reduce)': { opacity: 1, animation: 'none' },
});

function Hero({ business }: { business: PublicBusiness }) {
  const cover = mediaUrl(business.coverUrl);

  return (
    <Box
      sx={{
        position: 'relative',
        overflow: 'hidden',
        minHeight: { xs: 560, md: 640 },
        display: 'grid',
        placeItems: 'center',
        textAlign: 'center',
        color: '#fff',
        px: 2,
        pt: 10,
        pb: 6,
        backgroundColor: 'primary.main',
        '&::before': {
          content: '""',
          position: 'absolute',
          inset: 0,
          backgroundImage: cover
            ? `linear-gradient(180deg, rgba(10,12,20,0.55) 0%, rgba(10,12,20,0.45) 50%, rgba(10,12,20,0.75) 100%), url("${cover}")`
            : (theme) => `radial-gradient(circle at 30% 20%, ${theme.palette.primary.light} 0%, ${theme.palette.primary.dark} 55%, #111214 100%)`,
          backgroundSize: cover ? 'cover' : '160% 160%',
          backgroundPosition: 'center',
          animation: cover ? `${slowZoom} 14s ease-out forwards` : `${gradientDrift} 18s ease-in-out infinite`,
          '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
        },
      }}
    >
      <Stack alignItems="center" spacing={2} sx={{ maxWidth: 760, position: 'relative' }}>
        <Typography variant="overline" sx={{ letterSpacing: '0.35em', color: 'rgba(255,255,255,0.85)', ...heroLine(0) }}>
          Welcome
        </Typography>
        <Typography component="h1" sx={{ fontSize: { xs: 44, sm: 64, md: 84 }, fontWeight: 800, lineHeight: 1, letterSpacing: '-0.02em', ...heroLine(1) }}>
          {business.name}
        </Typography>
        {business.tagline ? (
          <Typography sx={{ fontSize: { xs: 17, md: 20 }, maxWidth: 620, ...heroLine(2) }}>
            <Box component="span" sx={{ opacity: 0.92 }}>{business.tagline}</Box>
          </Typography>
        ) : null}
        <Button
          component={RouterLink}
          to={`/b/${business.slug}/book`}
          variant="contained"
          size="large"
          startIcon={<CalendarMonthRoundedIcon />}
          sx={{
            mt: 2,
            borderRadius: 999,
            px: 4,
            py: 1.5,
            fontSize: 17,
            boxShadow: '0 10px 30px rgba(0,0,0,0.25)',
            ...heroLine(3),
            animation: `${riseIn} 800ms cubic-bezier(0.22, 1, 0.36, 1) 570ms forwards, ${softPulse} 2.8s ease-in-out 1.6s infinite`,
            transition: 'transform 200ms ease',
            '&:hover': { transform: 'translateY(-2px)' },
          }}
        >
          See free times & prices
        </Button>
      </Stack>
    </Box>
  );
}

function ServiceGroups({ slug, services }: { slug: string; services: PublicService[] }) {
  const groups = useMemo(() => {
    const map = new Map<string, PublicService[]>();
    services.forEach((service) => {
      const key = service.category?.trim() || 'Services';
      map.set(key, [...(map.get(key) ?? []), service]);
    });
    return [...map.entries()];
  }, [services]);

  return (
    <Grid container spacing={3}>
      {groups.map(([category, items], groupIndex) => (
        <Grid item xs={12} md={groups.length > 1 ? 6 : 12} key={category}>
          <Reveal delay={groupIndex * 120} sx={{ height: '100%' }}>
          <Paper variant="outlined" sx={{ p: { xs: 2.5, md: 3.5 }, height: '100%', borderRadius: 4 }}>
            <Typography variant="h6" fontWeight={800} sx={{ mb: 2 }}>
              {category}
            </Typography>
            <Stack spacing={1}>
              {items.map((service, index) => (
                <Reveal key={service.id} delay={120 + index * 90}>
                <Stack
                  component={RouterLink}
                  to={`/b/${slug}/book?service=${service.id}`}
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
                    <Box sx={{ width: 72, height: 72, borderRadius: 2.5, overflow: 'hidden', flexShrink: 0 }}>
                      <Box
                        component="img"
                        className="service-img"
                        src={mediaUrl(service.imageUrl)}
                        alt=""
                        sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
                      />
                    </Box>
                  ) : null}
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Stack direction="row" alignItems="baseline" spacing={1}>
                      <Typography fontWeight={700} noWrap>
                        {service.name}
                      </Typography>
                      <Box sx={{ flex: 1, borderBottom: '1px dotted', borderColor: 'divider', minWidth: 16, transform: 'translateY(-4px)' }} />
                      <Typography fontWeight={800} sx={{ whiteSpace: 'nowrap' }}>
                        {formatMoney(service.price, service.currency)}
                        {service.pricePerParticipant ? (
                          <Typography component="span" variant="caption" color="text.secondary">
                            {' '}
                            / person
                          </Typography>
                        ) : null}
                      </Typography>
                    </Stack>
                    {service.description ? (
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                        {service.description}
                      </Typography>
                    ) : null}
                    <Stack direction="row" spacing={2} sx={{ mt: 0.5, color: 'text.secondary' }}>
                      <Meta icon={<AccessTimeRoundedIcon sx={{ fontSize: 16 }} />} text={formatDuration(service.durationMinutes)} />
                      {service.maxParticipants > 1 ? (
                        <Meta icon={<GroupRoundedIcon sx={{ fontSize: 16 }} />} text={`Up to ${service.maxParticipants}`} />
                      ) : null}
                    </Stack>
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

function FindUs({ business }: { business: PublicBusiness }) {
  const mapsQuery = encodeURIComponent([business.name, business.address].filter(Boolean).join(', '));
  const showMap = business.showMap && Boolean(business.address);

  return (
    <Box id="find-us" sx={{ py: { xs: 6, md: 10 }, bgcolor: 'background.paper', borderTop: 1, borderColor: 'divider' }}>
      <Container maxWidth="lg">
        <SectionTitle eyebrow="Where we are" title="How to find us" />
        <Grid container spacing={3}>
          {showMap ? (
            <Grid item xs={12} md={6}>
              <Reveal sx={{ height: '100%' }}>
              <Paper variant="outlined" sx={{ overflow: 'hidden', borderRadius: 4, height: { xs: 280, md: '100%' }, minHeight: 280 }}>
                <Box
                  component="iframe"
                  title={`Map of ${business.name}`}
                  src={`https://www.google.com/maps?q=${mapsQuery}&output=embed`}
                  loading="lazy"
                  referrerPolicy="no-referrer-when-downgrade"
                  sx={{ border: 0, width: '100%', height: '100%', display: 'block' }}
                />
              </Paper>
              </Reveal>
            </Grid>
          ) : null}
          <Grid item xs={12} md={showMap ? 6 : 12}>
            <Reveal delay={showMap ? 120 : 0} sx={{ height: '100%' }}>
            <Paper variant="outlined" sx={{ p: { xs: 3, md: 4 }, borderRadius: 4, height: '100%' }}>
              <Typography variant="h5" fontWeight={800}>
                {business.name}
              </Typography>
              <Stack spacing={1.25} sx={{ mt: 2 }}>
                {business.address ? <Meta icon={<PlaceRoundedIcon color="primary" fontSize="small" />} text={business.address} /> : null}
                {business.phone ? <Meta icon={<PhoneRoundedIcon color="primary" fontSize="small" />} text={business.phone} /> : null}
              </Stack>
              <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>
                Opening hours
              </Typography>
              <OpeningHours hours={business.openingHours} />
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 3 }}>
                {business.address ? (
                  <Button
                    variant="outlined"
                    startIcon={<MapRoundedIcon />}
                    href={`https://www.google.com/maps/search/?api=1&query=${mapsQuery}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    sx={{ borderRadius: 999 }}
                  >
                    Open in Google Maps
                  </Button>
                ) : null}
                <Button component={RouterLink} to={`/b/${business.slug}/book`} variant="contained" sx={{ borderRadius: 999 }}>
                  Book an appointment
                </Button>
              </Stack>
            </Paper>
            </Reveal>
          </Grid>
        </Grid>
      </Container>
    </Box>
  );
}

function SectionTitle({ eyebrow, title }: { eyebrow: string; title: string }) {
  return (
    <Reveal>
      <Stack alignItems="center" spacing={1} sx={{ mb: { xs: 4, md: 5 }, textAlign: 'center' }}>
        <Typography variant="overline" color="primary" sx={{ letterSpacing: '0.3em', fontWeight: 700 }}>
          {eyebrow}
        </Typography>
        <Typography component="h2" sx={{ fontSize: { xs: 30, md: 42 }, fontWeight: 800, letterSpacing: '-0.02em' }}>
          {title}
        </Typography>
      </Stack>
    </Reveal>
  );
}

function Meta({ icon, text }: { icon: React.ReactNode; text: string }) {
  return (
    <Stack direction="row" spacing={0.75} alignItems="center">
      {icon}
      <Typography variant="body2">{text}</Typography>
    </Stack>
  );
}

function OpeningHours({ hours }: { hours: DayInterval[] }) {
  const today = ((new Date().getDay() + 6) % 7) + 1;
  return (
    <Stack spacing={0.5}>
      {DAY_NAMES.map((name, index) => {
        const day = index + 1;
        const intervals = hours.filter((interval) => interval.dayOfWeek === day);
        return (
          <Stack key={name} direction="row" justifyContent="space-between" sx={{ fontWeight: day === today ? 700 : 400, maxWidth: 360 }}>
            <Typography variant="body2" fontWeight="inherit">
              {name}
            </Typography>
            <Typography variant="body2" fontWeight="inherit" color={intervals.length ? 'text.primary' : 'text.secondary'}>
              {intervals.length ? intervals.map(formatInterval).join(', ') : 'Closed'}
            </Typography>
          </Stack>
        );
      })}
    </Stack>
  );
}
