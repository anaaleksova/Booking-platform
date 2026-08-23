import { useState } from 'react';
import { useParams } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Container from '@mui/material/Container';
import Divider from '@mui/material/Divider';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { StatusChip } from '@/components/StatusChip';
import {
  useCancelPublicBooking,
  usePublicBookingByReference,
} from '@/features/publicBooking/usePublicBooking';
import { errorMessage } from '@/api/client';
import { formatLocalDate, formatLocalTime, formatMoney } from '@/utils/format';

/**
 * The customer's copy of their booking.
 *
 * <p>Reachable with the reference alone — the link that arrives by email. It
 * shows nothing about the business's other bookings, and cancelling here is
 * subject to the business's own deadline.
 */
export function BookingConfirmationPage() {
  const { reference = '' } = useParams();
  const { data: booking, isLoading, isError, error } = usePublicBookingByReference(reference);
  const cancelBooking = useCancelPublicBooking(reference);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  if (isLoading) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Skeleton variant="rounded" height={320} />
      </Container>
    );
  }

  if (isError || !booking) {
    return (
      <Container maxWidth="sm" sx={{ py: 8 }}>
        <Alert severity="error">{errorMessage(error, 'This booking could not be found.')}</Alert>
      </Container>
    );
  }

  const cancelled = booking.status === 'CANCELLED';

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default', py: { xs: 4, sm: 8 } }}>
      <Container maxWidth="sm">
        <Card>
          <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
            <Stack spacing={1} alignItems="center" textAlign="center" sx={{ mb: 3 }}>
              {!cancelled ? (
                <CheckCircleRoundedIcon color="success" sx={{ fontSize: 52 }} />
              ) : null}
              <Typography variant="h5">
                {cancelled ? 'Booking cancelled' : 'Booking confirmed'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {cancelled
                  ? 'This appointment has been cancelled.'
                  : `We've saved your appointment at ${booking.businessName}.`}
              </Typography>
              <StatusChip status={booking.status} size="medium" />
            </Stack>

            <Divider sx={{ mb: 3 }} />

            <Stack spacing={2}>
              <Row label="Service" value={booking.serviceName} />
              {booking.staffName ? <Row label="With" value={booking.staffName} /> : null}
              <Row label="Date" value={formatLocalDate(booking.localStartDateTime)} />
              <Row
                label="Time"
                value={`${formatLocalTime(booking.localStartDateTime)} – ${formatLocalTime(
                  booking.localEndDateTime,
                )} (${booking.timezone})`}
              />
              <Row label="Name" value={booking.customerName} />
              {booking.price != null ? (
                <Row label="Price" value={formatMoney(booking.price, booking.currency)} />
              ) : null}
              <Divider />
              <Row label="Business" value={booking.businessName} />
              {booking.businessAddress ? <Row label="Address" value={booking.businessAddress} /> : null}
              {booking.businessPhone ? <Row label="Phone" value={booking.businessPhone} /> : null}
              <Row label="Reference" value={booking.reference} />
            </Stack>

            {cancelError ? (
              <Alert severity="error" sx={{ mt: 3 }}>
                {cancelError}
              </Alert>
            ) : null}

            {!cancelled ? (
              <Stack spacing={1.5} sx={{ mt: 4 }}>
                {booking.cancellable ? (
                  <Button color="error" variant="outlined" onClick={() => setConfirmOpen(true)}>
                    Cancel this booking
                  </Button>
                ) : (
                  <Alert severity="info">
                    This booking can no longer be cancelled online. Please contact the business
                    {booking.businessPhone ? ` on ${booking.businessPhone}` : ''}.
                  </Alert>
                )}
                <Button href={`/book/${booking.businessSlug}`} variant="text">
                  Book another appointment
                </Button>
              </Stack>
            ) : (
              <Button href={`/book/${booking.businessSlug}`} variant="contained" fullWidth sx={{ mt: 4 }}>
                Book a new appointment
              </Button>
            )}
          </CardContent>
        </Card>
      </Container>

      <ConfirmDialog
        open={confirmOpen}
        title="Cancel this booking?"
        message="Your slot will be released and the business will be notified."
        confirmLabel="Cancel booking"
        busy={cancelBooking.isPending}
        onClose={() => setConfirmOpen(false)}
        onConfirm={() => {
          setCancelError(null);
          cancelBooking.mutate(undefined, {
            onSuccess: () => setConfirmOpen(false),
            onError: (mutationError) => {
              setCancelError(errorMessage(mutationError));
              setConfirmOpen(false);
            },
          });
        }}
      />
    </Box>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction="row" justifyContent="space-between" spacing={2}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" fontWeight={600} textAlign="right">
        {value}
      </Typography>
    </Stack>
  );
}
