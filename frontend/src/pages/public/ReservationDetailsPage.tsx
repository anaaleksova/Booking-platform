import { useState } from 'react';
import { Link as RouterLink, useLocation, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { StatusChip } from '@/components/StatusChip';
import { ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { cancelPublicReservation, usePublicBusiness, usePublicReservation } from '@/features/public/api';
import { BusinessShell } from '@/features/public/BusinessShell';
import { queryKeys } from '@/features/queryKeys';
import Container from '@mui/material/Container';
import { formatLocalDate, formatMoney, formatWindow, pluralize } from '@/utils/format';

/** The customer's own page for a reservation, reachable only with its unguessable reference. */
export function ReservationDetailsPage() {
  const reference = useParams().reference!;
  const justBooked = Boolean((useLocation().state as { justBooked?: boolean } | null)?.justBooked);
  const toast = useToast();
  const queryClient = useQueryClient();
  const { data, isLoading, isError, error } = usePublicReservation(reference);
  const business = usePublicBusiness(data?.businessSlug ?? '', Boolean(data));
  const [confirming, setConfirming] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  if (isLoading || (data && business.isLoading)) {
    return <LoadingState />;
  }
  if (isError || !data) {
    return (
      <BusinessShell>
        <Container maxWidth="sm" sx={{ py: 8 }}>
          <ErrorState error={error} />
        </Container>
      </BusinessShell>
    );
  }

  const cancel = async () => {
    setCancelling(true);
    try {
      const updated = await cancelPublicReservation(reference);
      queryClient.setQueryData(queryKeys.publicReservation(reference), updated);
      toast.success('Your reservation was cancelled');
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setCancelling(false);
      setConfirming(false);
    }
  };

  return (
    <BusinessShell business={business.data} footer={false}>
      <Container maxWidth="sm" sx={{ py: { xs: 3, md: 6 } }}>
      {justBooked && data.status !== 'CANCELLED' ? (
        <Stack alignItems="center" spacing={1} sx={{ mb: 3, textAlign: 'center' }}>
          <CheckCircleRoundedIcon color="success" sx={{ fontSize: 56 }} />
          <Typography variant="h4">You're booked!</Typography>
          <Typography color="text.secondary">
            Keep this page's link — you can come back to it to view or cancel your reservation.
          </Typography>
        </Stack>
      ) : null}

      <Paper variant="outlined" sx={{ p: { xs: 3, sm: 4 } }}>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
          <div>
            <Typography variant="overline" color="text.secondary">
              Reservation
            </Typography>
            <Typography variant="h5">{data.serviceName}</Typography>
          </div>
          <StatusChip status={data.status} size="medium" />
        </Stack>

        <Stack spacing={1.5} sx={{ mt: 3 }}>
          <Row label="Date" value={formatLocalDate(data.localStartTime)} />
          <Row label="Time" value={`${formatWindow(data)} (${data.timezone})`} />
          <Row label="Guests" value={pluralize(data.numberOfParticipants, 'person', 'people')} />
          {data.resources.length ? (
            <Row label="Reserved for you" value={data.resources.map((resource) => resource.name).join(', ')} />
          ) : null}
          {data.customerName ? <Row label="Name" value={data.customerName} /> : null}
        </Stack>

        <Divider sx={{ my: 3 }} />
        <Stack spacing={0.75}>
          <Row label="Service" value={formatMoney(data.originalPrice, data.currency)} />
          <Row
            label={data.discountCode ? `Discount (${data.discountCode})` : 'Discount'}
            value={data.discountAmount > 0 ? `− ${formatMoney(data.discountAmount, data.currency)}` : formatMoney(0, data.currency)}
          />
          <Row bold label="Total" value={formatMoney(data.finalPrice, data.currency)} />
        </Stack>

        <Divider sx={{ my: 3 }} />
        <Typography variant="subtitle2">{data.businessName}</Typography>
        <Typography variant="body2" color="text.secondary">
          {[data.businessAddress, data.businessPhone].filter(Boolean).join(' · ')}
        </Typography>

        {data.status === 'CANCELLED' ? (
          <Alert severity="info" sx={{ mt: 3 }}>
            This reservation has been cancelled.
          </Alert>
        ) : null}

        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 3 }}>
          <Button component={RouterLink} to={`/b/${data.businessSlug}/book`} variant="outlined" sx={{ borderRadius: 999 }}>
            Book something else
          </Button>
          {data.cancellable ? (
            <Button color="error" onClick={() => setConfirming(true)}>
              Cancel reservation
            </Button>
          ) : null}
        </Stack>
      </Paper>

      </Container>
      <ConfirmDialog
        open={confirming}
        title="Cancel this reservation?"
        message="The time will be released for other customers."
        confirmLabel="Cancel reservation"
        busy={cancelling}
        onConfirm={cancel}
        onClose={() => setConfirming(false)}
      />
    </BusinessShell>
  );
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <Stack direction="row" justifyContent="space-between" spacing={2}>
      <Typography variant={bold ? 'subtitle1' : 'body2'} color={bold ? 'text.primary' : 'text.secondary'} fontWeight={bold ? 700 : 400}>
        {label}
      </Typography>
      <Typography variant={bold ? 'subtitle1' : 'body2'} fontWeight={bold ? 700 : 500} textAlign="right">
        {value}
      </Typography>
    </Stack>
  );
}
