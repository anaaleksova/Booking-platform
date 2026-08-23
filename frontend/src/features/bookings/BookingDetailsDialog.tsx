import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import dayjs from 'dayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { StatusChip } from '@/components/StatusChip';
import { SlotPicker } from '@/features/availability/SlotPicker';
import {
  useCancelBooking,
  useChangeBookingOutcome,
  useRescheduleBooking,
} from '@/features/bookings/useBookings';
import { useToast } from '@/components/ToastProvider';
import { errorMessage } from '@/api/client';
import { formatBookingWindow, formatLocalDate, formatMoney } from '@/utils/format';
import type { AvailableSlot, Booking } from '@/types/api';

/**
 * One booking, and everything a business does with it: complete it, mark a
 * no-show, move it, or cancel it.
 *
 * <p>Rescheduling reuses the availability engine with the booking excluded,
 * so its current slot does not count as a conflict with itself.
 */
export function BookingDetailsDialog({
  booking,
  open,
  onClose,
}: {
  booking: Booking | null;
  open: boolean;
  onClose: () => void;
}) {
  const toast = useToast();
  const cancelBooking = useCancelBooking();
  const changeOutcome = useChangeBookingOutcome();
  const reschedule = useRescheduleBooking();

  const [mode, setMode] = useState<'view' | 'reschedule'>('view');
  const [date, setDate] = useState('');
  const [slot, setSlot] = useState<AvailableSlot | null>(null);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  if (!booking) {
    return null;
  }

  const isActive = booking.status === 'CONFIRMED' || booking.status === 'PENDING';

  const close = () => {
    setMode('view');
    setSlot(null);
    setActionError(null);
    setCancelReason('');
    onClose();
  };

  const run = async (action: () => Promise<unknown>, message: string) => {
    setActionError(null);
    try {
      await action();
      toast.success(message);
      close();
    } catch (error) {
      setActionError(errorMessage(error));
    }
  };

  const startReschedule = () => {
    setDate(dayjs(booking.localStartDateTime).format('YYYY-MM-DD'));
    setSlot(null);
    setMode('reschedule');
  };

  return (
    <>
      <Dialog open={open} onClose={close} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2}>
            <span>{booking.serviceName}</span>
            <StatusChip status={booking.status} />
          </Stack>
        </DialogTitle>
        <DialogContent dividers>
          {mode === 'view' ? (
            <Stack spacing={1.5}>
              <Detail label="When" value={`${formatLocalDate(booking.localStartDateTime)} · ${formatBookingWindow(booking)}`} />
              <Detail label="Customer" value={booking.customerName ?? '—'} />
              {booking.customerPhone ? <Detail label="Phone" value={booking.customerPhone} /> : null}
              {booking.customerEmail ? <Detail label="Email" value={booking.customerEmail} /> : null}
              {booking.staffName ? <Detail label="Staff" value={booking.staffName} /> : null}
              {booking.resourceName ? <Detail label="Resource" value={booking.resourceName} /> : null}
              {booking.price != null ? (
                <Detail label="Price" value={formatMoney(booking.price, booking.currency)} />
              ) : null}
              <Detail label="Booked via" value={booking.source === 'PUBLIC' ? 'Public page' : 'Dashboard'} />
              {booking.customerNotes ? <Detail label="Customer notes" value={booking.customerNotes} /> : null}
              {booking.internalNotes ? <Detail label="Internal notes" value={booking.internalNotes} /> : null}
              {actionError ? <Alert severity="error">{actionError}</Alert> : null}
            </Stack>
          ) : (
            <Stack spacing={2.5}>
              <Typography variant="body2" color="text.secondary">
                Currently {formatLocalDate(booking.localStartDateTime)} at{' '}
                {formatBookingWindow(booking)}.
              </Typography>
              <DatePicker
                label="New date"
                value={dayjs(date)}
                onChange={(value) => {
                  if (value) {
                    setDate(value.format('YYYY-MM-DD'));
                    setSlot(null);
                  }
                }}
                slotProps={{ textField: { fullWidth: true, size: 'small' } }}
              />
              <SlotPicker
                serviceId={booking.serviceId}
                date={date}
                staffId={booking.staffId}
                resourceId={booking.resourceId}
                excludeBookingId={booking.id}
                selected={slot}
                onSelect={setSlot}
              />
              {actionError ? <Alert severity="error">{actionError}</Alert> : null}
            </Stack>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2, flexWrap: 'wrap', gap: 1 }}>
          {mode === 'view' ? (
            <>
              {isActive ? (
                <>
                  <Button
                    color="error"
                    onClick={() => setConfirmCancel(true)}
                    disabled={cancelBooking.isPending}
                  >
                    Cancel booking
                  </Button>
                  <Divider orientation="vertical" flexItem />
                  <Button
                    onClick={() =>
                      void run(
                        () => changeOutcome.mutateAsync({ id: booking.id, outcome: 'no-show' }),
                        'Marked as no-show.',
                      )
                    }
                    disabled={changeOutcome.isPending}
                  >
                    No-show
                  </Button>
                  <Button
                    onClick={() =>
                      void run(
                        () => changeOutcome.mutateAsync({ id: booking.id, outcome: 'complete' }),
                        'Marked as completed.',
                      )
                    }
                    disabled={changeOutcome.isPending}
                  >
                    Completed
                  </Button>
                  <Button variant="contained" onClick={startReschedule}>
                    Reschedule
                  </Button>
                </>
              ) : (
                <Button onClick={close}>Close</Button>
              )}
            </>
          ) : (
            <>
              <Button onClick={() => setMode('view')}>Back</Button>
              <Button
                variant="contained"
                disabled={!slot || reschedule.isPending}
                onClick={() =>
                  void run(
                    () =>
                      reschedule.mutateAsync({
                        id: booking.id,
                        startDateTime: `${date}T${slot!.time}`,
                      }),
                    'Booking moved.',
                  )
                }
              >
                {reschedule.isPending ? 'Moving…' : 'Confirm new time'}
              </Button>
            </>
          )}
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmCancel}
        title="Cancel this booking?"
        message="The slot is released immediately and the customer is notified."
        confirmLabel="Cancel booking"
        busy={cancelBooking.isPending}
        onClose={() => setConfirmCancel(false)}
        onConfirm={() =>
          void run(
            () => cancelBooking.mutateAsync({ id: booking.id, reason: cancelReason || undefined }),
            'Booking cancelled.',
          ).then(() => setConfirmCancel(false))
        }
      />
    </>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction="row" spacing={2}>
      <Typography variant="body2" color="text.secondary" sx={{ minWidth: 120 }}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 500, whiteSpace: 'pre-wrap' }}>
        {value}
      </Typography>
    </Stack>
  );
}

/** Kept out of the dialog body: the cancel reason field is optional. */
export function CancelReasonField({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <TextField
      label="Reason (optional)"
      fullWidth
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}
