import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import AccessTimeRoundedIcon from '@mui/icons-material/AccessTimeRounded';
import MailRoundedIcon from '@mui/icons-material/MailRounded';
import MapRoundedIcon from '@mui/icons-material/MapRounded';
import PhoneRoundedIcon from '@mui/icons-material/PhoneRounded';
import PlaceRoundedIcon from '@mui/icons-material/PlaceRounded';
import { bookLabel } from '@/features/public/freeBooking';
import { Reveal } from '@/features/public/Reveal';
import { useSiteData } from '../render/SiteData';
import type { ContactSection } from '../types';
import { Meta, OpeningHours, SectionBody, SectionHeading, textOr, type SectionProps } from './shared';

function useContact(section: ContactSection) {
  const { business, services } = useSiteData();
  const mapsQuery = encodeURIComponent([business.name, business.address].filter(Boolean).join(', '));
  return {
    business,
    bookButton: bookLabel(services),
    mapsQuery,
    eyebrow: textOr(section.content.eyebrow, 'Where we are'),
    title: textOr(section.content.title, 'How to find us'),
  };
}

function MapsButton({ mapsQuery }: { mapsQuery: string }) {
  return (
    <Button
      variant="outlined"
      startIcon={<MapRoundedIcon />}
      href={`https://www.google.com/maps/search/?api=1&query=${mapsQuery}`}
      target="_blank"
      rel="noopener noreferrer"
      sx={{ borderRadius: 'var(--site-radius-button, 999px)' }}
    >
      Open in Google Maps
    </Button>
  );
}

/** Name, address, phone, optionally the hours, and the buttons: the card every contact layout has. */
function InfoCard({ section, sx }: { section: ContactSection; sx?: object }) {
  const { business, bookButton, mapsQuery } = useContact(section);
  return (
    <Paper variant="outlined" sx={{ p: { xs: 3, md: 4 }, borderRadius: 'var(--site-radius-card, 56px)', height: '100%', ...sx }}>
      <Typography variant="h5" component="h3" fontWeight={800}>
        {business.name}
      </Typography>
      <Stack spacing={1.25} sx={{ mt: 2 }}>
        {business.address ? <Meta icon={<PlaceRoundedIcon color="primary" fontSize="small" />} text={business.address} /> : null}
        {business.phone ? <Meta icon={<PhoneRoundedIcon color="primary" fontSize="small" />} text={business.phone} /> : null}
      </Stack>
      {section.content.showHours ? (
        <>
          <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>
            Opening hours
          </Typography>
          <OpeningHours hours={business.openingHours} special={business.specialHours} />
        </>
      ) : null}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 3 }}>
        {business.address ? <MapsButton mapsQuery={mapsQuery} /> : null}
        <Button component={RouterLink} to={`/b/${business.slug}/book`} variant="contained" sx={{ borderRadius: 'var(--site-radius-button, 999px)' }}>
          {bookButton}
        </Button>
      </Stack>
    </Paper>
  );
}

/** "How to find us" with the map beside it, as the page has always had it. */
export function ContactMapInfo({ section }: SectionProps<ContactSection>) {
  const { business, mapsQuery, eyebrow, title } = useContact(section);
  const showMap = Boolean(business.address);
  return (
    <SectionBody sx={{ borderTop: 1, borderColor: 'divider' }}>
      <SectionHeading section={section} eyebrow={eyebrow} title={title} />
      <Grid container spacing={3}>
        {showMap ? (
          <Grid item xs={12} md={6}>
            <Reveal sx={{ height: '100%' }}>
              <Paper variant="outlined" sx={{ overflow: 'hidden', borderRadius: 'var(--site-radius-card, 56px)', height: { xs: 280, md: '100%' }, minHeight: 280 }}>
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
            <InfoCard section={section} />
          </Reveal>
        </Grid>
      </Grid>
    </SectionBody>
  );
}

/** The same card without a map. */
export function ContactInfoOnly({ section }: SectionProps<ContactSection>) {
  const { eyebrow, title } = useContact(section);
  return (
    <SectionBody sx={{ borderTop: 1, borderColor: 'divider' }}>
      <SectionHeading section={section} eyebrow={eyebrow} title={title} />
      <Reveal>
        <InfoCard section={section} />
      </Reveal>
    </SectionBody>
  );
}

/** The opening hours large on one side, how to reach the business on the other. */
export function ContactWithHours({ section }: SectionProps<ContactSection>) {
  const { business, bookButton, mapsQuery, eyebrow, title } = useContact(section);
  return (
    <SectionBody>
      <SectionHeading section={section} eyebrow={eyebrow} title={title} />
      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Reveal sx={{ height: '100%' }}>
            <Paper variant="outlined" sx={{ p: { xs: 3, md: 4 }, borderRadius: 'var(--site-radius-card, 56px)', height: '100%' }}>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
                <AccessTimeRoundedIcon color="primary" />
                <Typography variant="h6" component="h3" fontWeight={800}>
                  Opening hours
                </Typography>
              </Stack>
              <OpeningHours hours={business.openingHours} special={business.specialHours} maxWidth="100%" />
            </Paper>
          </Reveal>
        </Grid>
        <Grid item xs={12} md={6}>
          <Reveal delay={120} sx={{ height: '100%' }}>
            <Paper variant="outlined" sx={{ p: { xs: 3, md: 4 }, borderRadius: 'var(--site-radius-card, 56px)', height: '100%' }}>
              <Typography variant="h6" component="h3" fontWeight={800}>
                Get in touch
              </Typography>
              <Stack spacing={1.5} sx={{ mt: 2 }}>
                {business.address ? <Meta icon={<PlaceRoundedIcon color="primary" fontSize="small" />} text={business.address} /> : null}
                {business.phone ? <Meta icon={<PhoneRoundedIcon color="primary" fontSize="small" />} text={business.phone} /> : null}
                {business.email ? <Meta icon={<MailRoundedIcon color="primary" fontSize="small" />} text={business.email} /> : null}
              </Stack>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 3 }}>
                {business.address ? <MapsButton mapsQuery={mapsQuery} /> : null}
                <Button component={RouterLink} to={`/b/${business.slug}/book`} variant="contained" sx={{ borderRadius: 'var(--site-radius-button, 999px)' }}>
                  {bookButton}
                </Button>
              </Stack>
            </Paper>
          </Reveal>
        </Grid>
      </Grid>
    </SectionBody>
  );
}
