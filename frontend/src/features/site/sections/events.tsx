import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import dayjs from 'dayjs';
import { EventsSection as EventCards } from '@/features/public/EventsSection';
import { Reveal } from '@/features/public/Reveal';
import { formatEventHours } from '@/utils/format';
import { useSiteData } from '../render/SiteData';
import type { EventsSection } from '../types';
import { SectionBody, SectionHeading, textOr, type SectionProps } from './shared';

function useHeading(section: EventsSection, count: number) {
  return {
    eyebrow: textOr(section.content.eyebrow, "What's on"),
    title: textOr(section.content.title, count === 1 ? 'Coming up' : 'Upcoming events'),
  };
}

/** The events as cards, as the page has always had them. Nothing when nothing is on. */
export function EventsCardsLayout({ section }: SectionProps<EventsSection>) {
  const { business, events } = useSiteData();
  const { eyebrow, title } = useHeading(section, events.length);
  return <EventCards slug={business.slug} events={events} title={() => <SectionHeading section={section} eyebrow={eyebrow} title={title} />} />;
}

/** A compact programme: date, title, time, one row each. */
export function EventsListLayout({ section }: SectionProps<EventsSection>) {
  const { business, events } = useSiteData();
  const { eyebrow, title } = useHeading(section, events.length);
  if (events.length === 0) {
    return null;
  }
  return (
    <SectionBody maxWidth="md">
      <SectionHeading section={section} eyebrow={eyebrow} title={title} />
      <Stack spacing={1.5}>
        {events.map((event, index) => {
          const date = dayjs(event.date);
          const full = event.booking === 'FULL';
          return (
            <Reveal key={event.id} delay={index * 80}>
              <Paper variant="outlined" sx={{ p: 2, borderRadius: 'var(--site-radius-card, 56px)' }}>
                <Stack direction="row" spacing={2.5} alignItems="center">
                  <Stack alignItems="center" sx={{ width: 56, flexShrink: 0, py: 0.5, borderRadius: 2, bgcolor: 'var(--site-primary)', color: 'var(--site-on-primary)' }}>
                    <Typography variant="caption" fontWeight={700} sx={{ textTransform: 'uppercase', lineHeight: 1.4 }}>
                      {date.format('MMM')}
                    </Typography>
                    <Typography fontWeight={800} sx={{ fontSize: 22, lineHeight: 1 }}>
                      {date.format('D')}
                    </Typography>
                  </Stack>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography fontWeight={800} component="h3">
                      {event.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {date.format('dddd')} · {formatEventHours(event)}
                    </Typography>
                  </Box>
                  {full ? (
                    <Typography variant="body2" fontWeight={700} color="text.secondary">
                      Fully booked
                    </Typography>
                  ) : event.booking === 'OPEN' && event.serviceId ? (
                    <Button component={RouterLink} to={`/b/${business.slug}/book?service=${event.serviceId}&date=${event.date}`} variant="outlined" size="small" sx={{ borderRadius: 'var(--site-radius-button, 999px)' }}>
                      Book
                    </Button>
                  ) : null}
                </Stack>
              </Paper>
            </Reveal>
          );
        })}
      </Stack>
    </SectionBody>
  );
}
