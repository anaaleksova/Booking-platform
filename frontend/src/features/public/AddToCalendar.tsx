import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import EventRoundedIcon from '@mui/icons-material/EventRounded';
import type { PublicReservation } from '@/types/api';

/** 20261019T120000Z — the UTC form both calendar links take. */
function compact(iso: string) {
  return new Date(iso).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
}

/**
 * "Add to calendar" for the customer's reservation. Each button opens their
 * own calendar with the event filled in; nothing is added until they save it.
 */
export function AddToCalendar({ reservation }: { reservation: PublicReservation }) {
  const title = `${reservation.serviceName} at ${reservation.businessName}`;
  const with_ = reservation.resources.map((resource) => resource.name).join(', ');
  const details = [
    with_ ? `With: ${with_}` : null,
    reservation.businessPhone ? `${reservation.businessName}: ${reservation.businessPhone}` : null,
    `View or manage your reservation: ${window.location.origin}/booking/${reservation.reference}`,
  ]
    .filter(Boolean)
    .join('\n');
  const location = reservation.businessAddress ?? '';
  const q = encodeURIComponent;

  const google =
    `https://calendar.google.com/calendar/render?action=TEMPLATE&text=${q(title)}` +
    `&dates=${compact(reservation.startTime)}/${compact(reservation.endTime)}&details=${q(details)}&location=${q(location)}`;
  const outlook =
    'https://outlook.live.com/calendar/0/deeplink/compose?path=%2Fcalendar%2Faction%2Fcompose&rru=addevent' +
    `&subject=${q(title)}&startdt=${q(new Date(reservation.startTime).toISOString())}` +
    `&enddt=${q(new Date(reservation.endTime).toISOString())}&body=${q(details)}&location=${q(location)}`;

  return (
    <Stack spacing={1}>
      <Typography variant="subtitle2" sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
        <EventRoundedIcon fontSize="small" color="action" /> Add to your calendar
      </Typography>
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <Button size="small" variant="outlined" href={google} target="_blank" rel="noopener" sx={{ borderRadius: 'var(--site-radius-button, 999px)' }}>
          Google
        </Button>
        <Button size="small" variant="outlined" href={outlook} target="_blank" rel="noopener" sx={{ borderRadius: 'var(--site-radius-button, 999px)' }}>
          Outlook
        </Button>
        <Button
          size="small"
          variant="outlined"
          href={`/api/public/reservations/${reservation.reference}/calendar.ics`}
          sx={{ borderRadius: 'var(--site-radius-button, 999px)' }}
        >
          Apple / other
        </Button>
      </Stack>
    </Stack>
  );
}
