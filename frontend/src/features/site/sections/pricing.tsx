import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { isFree } from '@/features/public/freeBooking';
import { Reveal } from '@/features/public/Reveal';
import type { PublicService } from '@/types/api';
import { formatDuration, formatMoney } from '@/utils/format';
import { useSiteData } from '../render/SiteData';
import type { PricingSection } from '../types';
import { SectionBody, SectionHeading, textOr, useSectionServices, type SectionProps } from './shared';

function priceText(service: PublicService) {
  if (isFree(service)) {
    return 'Free';
  }
  return `${formatMoney(service.price, service.currency)}${service.pricePerParticipant ? ' / person' : ''}`;
}

function usePricing(section: PricingSection) {
  return {
    services: useSectionServices(section.content.source, section.content.serviceIds),
    eyebrow: textOr(section.content.eyebrow, 'Prices'),
    title: textOr(section.content.title, 'Our prices'),
  };
}

/** A card per service with the price large; the highlighted one stands out in the primary colour. */
export function PricingCards({ section }: SectionProps<PricingSection>) {
  const { business } = useSiteData();
  const { services, eyebrow, title } = usePricing(section);
  return (
    <SectionBody>
      <SectionHeading section={section} eyebrow={eyebrow} title={title} />
      <Grid container spacing={3} justifyContent="center" alignItems="stretch">
        {services.map((service, index) => {
          const highlighted = service.id === section.content.highlightServiceId;
          return (
            <Grid item xs={12} sm={6} md={4} key={service.id}>
              <Reveal delay={(index % 3) * 100} sx={{ height: '100%' }}>
                <Paper
                  variant="outlined"
                  sx={{
                    p: 3.5,
                    height: '100%',
                    borderRadius: 'var(--site-radius-card, 56px)',
                    display: 'flex',
                    flexDirection: 'column',
                    textAlign: 'center',
                    ...(highlighted ? { bgcolor: 'var(--site-primary)', color: 'var(--site-on-primary)', borderColor: 'transparent', transform: { md: 'scale(1.04)' } } : {}),
                  }}
                >
                  {highlighted ? <Chip label="Most popular" size="small" sx={{ alignSelf: 'center', mb: 1.5, bgcolor: 'var(--site-accent)', color: '#fff', fontWeight: 700 }} /> : null}
                  <Typography variant="h6" component="h3" fontWeight={800}>
                    {service.name}
                  </Typography>
                  <Typography sx={{ fontSize: 40, fontWeight: 800, my: 1.5, letterSpacing: '-0.02em' }}>{priceText(service)}</Typography>
                  <Typography variant="body2" sx={{ opacity: 0.75 }}>
                    {formatDuration(service.durationMinutes)}
                  </Typography>
                  {service.description ? (
                    <Typography variant="body2" sx={{ mt: 1.5, opacity: 0.85 }}>
                      {service.description}
                    </Typography>
                  ) : null}
                  <Box sx={{ flex: 1 }} />
                  <Button
                    component={RouterLink}
                    to={`/b/${business.slug}/book?service=${service.id}`}
                    variant={highlighted ? 'contained' : 'outlined'}
                    sx={{ mt: 3, borderRadius: 'var(--site-radius-button, 999px)', ...(highlighted ? { bgcolor: 'var(--site-on-primary)', color: 'var(--site-primary)', '&:hover': { bgcolor: 'var(--site-on-primary)', opacity: 0.9 } } : {}) }}
                  >
                    Book
                  </Button>
                </Paper>
              </Reveal>
            </Grid>
          );
        })}
      </Grid>
    </SectionBody>
  );
}

/** A plain table: service, duration, price. */
export function PricingTable({ section }: SectionProps<PricingSection>) {
  const { services, eyebrow, title } = usePricing(section);
  return (
    <SectionBody maxWidth="md">
      <SectionHeading section={section} eyebrow={eyebrow} title={title} />
      <Paper variant="outlined" sx={{ borderRadius: 'var(--site-radius-card, 56px)', overflow: 'hidden' }}>
        <Table aria-labelledby={`section-${section.id}-title`}>
          <TableHead>
            <TableRow>
              <TableCell>Service</TableCell>
              <TableCell>Duration</TableCell>
              <TableCell align="right">Price</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {services.map((service) => (
              <TableRow key={service.id} sx={service.id === section.content.highlightServiceId ? { '& td': { fontWeight: 800, color: 'var(--site-accent)' } } : undefined}>
                <TableCell>{service.name}</TableCell>
                <TableCell>{formatDuration(service.durationMinutes)}</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
                  {priceText(service)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </SectionBody>
  );
}
