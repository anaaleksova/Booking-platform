import { Link as RouterLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Container from '@mui/material/Container';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import AccessTimeRoundedIcon from '@mui/icons-material/AccessTimeRounded';
import CelebrationRoundedIcon from '@mui/icons-material/CelebrationRounded';
import MusicNoteRoundedIcon from '@mui/icons-material/MusicNoteRounded';
import dayjs from 'dayjs';
import { mediaUrl } from '@/features/media/api';
import { Reveal } from '@/features/public/Reveal';
import type { PublicEvent } from '@/types/api';
import { formatEventHours } from '@/utils/format';

const CARD_WIDTH = 340;
const IMAGE_HEIGHT = 180;

/**
 * "What's on":the events the business is showing right now, soonest first.
 * A bookable event links into the booking flow on its own day; one that is
 * fully booked says so instead. Renders nothing when there are no events.
 */
export function EventsSection({
  slug,
  events,
  title,
}: {
  slug: string;
  events: PublicEvent[];
  title: (eyebrow: string, heading: string) => React.ReactNode;
}) {
  if (events.length === 0) {
    return null;
  }
  return (
    <Box id="events" sx={{ py: { xs: 5, md: 7 } }}>
      <Container maxWidth="lg">
        {title("What's on", events.length === 1 ? 'Coming up' : 'Upcoming events')}
        {/* Cards keep one size however many there are; they wrap and stay centred. */}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'center', gap: 3 }}>
          {events.map((event, index) => (
            <Box key={event.id} sx={{ width: CARD_WIDTH, maxWidth: '100%' }}>
              <Reveal delay={index * 120} sx={{ height: '100%' }}>
                <EventCard slug={slug} event={event} />
              </Reveal>
            </Box>
          ))}
        </Box>
      </Container>
    </Box>
  );
}

export function EventCard({ slug, event }: { slug: string; event: PublicEvent }) {
  const date = dayjs(event.date);
  const image = mediaUrl(event.imageUrl);
  const full = event.booking === 'FULL';

  return (
    <Paper variant="outlined" sx={{ borderRadius: 4, overflow: 'hidden', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box
        sx={{
          position: 'relative',
          height: IMAGE_HEIGHT,
          flexShrink: 0,
          bgcolor: 'primary.main',
          backgroundImage: image ? `url("${image}")` : undefined,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
          display: 'grid',
          placeItems: 'center',
          color: 'primary.contrastText',
        }}
      >
        {image ? null : <CelebrationRoundedIcon sx={{ fontSize: 48, opacity: 0.6 }} />}
        {/* A calendar-leaf date in the corner. */}
        <Stack
          alignItems="center"
          sx={{ position: 'absolute', top: 12, left: 12, px: 1.25, py: 0.5, borderRadius: 2, bgcolor: 'background.paper', color: 'text.primary', boxShadow: 2, minWidth: 52 }}
        >
          <Typography variant="caption" fontWeight={700} color="primary" sx={{ textTransform: 'uppercase', lineHeight: 1.4 }}>
            {date.format('ddd')}
          </Typography>
          <Typography fontWeight={800} fontSize={22} lineHeight={1}>
            {date.format('D')}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', lineHeight: 1.4 }}>
            {date.format('MMM')}
          </Typography>
        </Stack>
        {full ? (
          <Chip label="Fully booked" color="error" sx={{ position: 'absolute', top: 12, right: 12, fontWeight: 700 }} />
        ) : null}
      </Box>
      <Stack spacing={1} sx={{ p: 2.5, flex: 1 }}>
        <Typography variant="h6" fontWeight={800} lineHeight={1.25}>
          {event.title}
        </Typography>
        <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap sx={{ color: 'text.secondary' }}>
          {event.performer ? <Meta icon={<MusicNoteRoundedIcon sx={{ fontSize: 18 }} />} text={event.performer} /> : null}
          <Meta icon={<AccessTimeRoundedIcon sx={{ fontSize: 18 }} />} text={formatEventHours(event)} />
        </Stack>
        {event.description ? (
          <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-line', display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
            {event.description}
          </Typography>
        ) : null}
        <Box sx={{ flex: 1 }} />
        {event.booking === 'OPEN' && event.serviceId ? (
          <Button
            component={RouterLink}
            to={`/b/${slug}/book?service=${event.serviceId}&date=${event.date}`}
            variant="contained"
            sx={{ borderRadius: 999, alignSelf: 'flex-start', mt: 1 }}
          >
            Reserve your spot
          </Button>
        ) : full ? (
          <Typography variant="body2" fontWeight={600} color="error.main" sx={{ mt: 1 }}>
            Fully booked. See you next time!
          </Typography>
        ) : null}
      </Stack>
    </Paper>
  );
}

function Meta({ icon, text }: { icon: React.ReactNode; text: string }) {
  return (
    <Stack direction="row" spacing={0.5} alignItems="center">
      {icon}
      <Typography variant="body2" fontWeight={600}>
        {text}
      </Typography>
    </Stack>
  );
}
