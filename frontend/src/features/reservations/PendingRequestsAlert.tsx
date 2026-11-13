import { Link as RouterLink } from 'react-router-dom';
import dayjs from 'dayjs';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import { pluralize } from '@/utils/format';
import { useReservations } from './api';

/** How far ahead requests are looked for: as far as anyone can book. */
export const REQUEST_HORIZON_DAYS = 365;

/** Booking requests still waiting for the owner, with a way to them. Nothing when there are none. */
export function PendingRequestsAlert() {
  const { data } = useReservations({
    from: dayjs().format('YYYY-MM-DD'),
    to: dayjs().add(REQUEST_HORIZON_DAYS, 'day').format('YYYY-MM-DD'),
    status: 'PENDING',
  });
  if (!data || data.length === 0) {
    return null;
  }
  return (
    <Alert
      severity="warning"
      sx={{ mb: 2 }}
      action={
        <Button component={RouterLink} to="/admin/reservations?status=PENDING" color="inherit" size="small">
          Review
        </Button>
      }
    >
      {pluralize(data.length, 'booking request')} waiting for your approval. Their times are held until you decide.
    </Alert>
  );
}
