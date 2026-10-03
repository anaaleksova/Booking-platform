import { useEffect, useState } from 'react';
import dayjs from 'dayjs';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { Link as RouterLink } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { StatusChip } from '@/components/StatusChip';
import { ResourcePicker, completeSelections } from '@/components/ResourcePicker';
import { LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import {
  useAdminAvailability,
  useChangeReservationStatus,
  useReservation,
  useRescheduleReservation,
} from '@/features/reservations/api';
import { SlotPicker } from '@/features/reservations/SlotPicker';
import type { ResourceSelection, Slot } from '@/types/api';
import {
  formatLocalDate,
  formatMoney,
  formatWindow,
  pluralize,
  toLocalDateTimeString,
} from '@/utils/format';

/**
 * One reservation: everything about it, the status actions, and changing its
 * time or assigned resources. Changes go through the same availability engine
 * as new reservations, minus the reservation's own current hold.
 */
export function ReservationDetailsDialog({ id, onClose }: { id: number | null; onClose: () => void }) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const toast = useToast();
  const { data: reservation, isLoading } = useReservation(id);
  const changeStatus = useChangeReservationStatus();
  const reschedule = useRescheduleReservation();

  const [editing, setEditing] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState('');
  const [date, setDate] = useState('');
  const [slot, setSlot] = useState<Slot | null>(null);
  const [selections, setSelections] = useState<ResourceSelection[]>([]);

  useEffect(() => {
    setEditing(false);
    setCancelling(false);
    setReason('');
  }, [id]);

  const availability = useAdminAvailability(
    editing ? reservation?.serviceId : undefined,
    date,
    reservation?.numberOfParticipants ?? 1,
    reservation?.id,
  );

  const startEditing = () => {
    if (!reservation) {
      return;
    }
    setDate(dayjs(reservation.localStartTime).format('YYYY-MM-DD'));
    setSlot(null);
    setSelections([]);
    setEditing(true);
  };

  // Pre-select the current time and resources once the day's slots arrive.
  useEffect(() => {
    if (!editing || !reservation || !availability.data || slot) {
      return;
    }
    const currentTime = dayjs(reservation.localStartTime).format('HH:mm');
    const current = availability.data.slots.find((item) => item.time.startsWith(currentTime));
    if (current && dayjs(reservation.localStartTime).format('YYYY-MM-DD') === date) {
      setSlot(current);
      const byRequirement = new Map<number, number[]>();
      reservation.resources.forEach((resource) => {
        if (resource.requirementId) {
          byRequirement.set(resource.requirementId, [
            ...(byRequirement.get(resource.requirementId) ?? []),
            resource.resourceId,
          ]);
        }
      });
      setSelections([...byRequirement].map(([requirementId, resourceIds]) => ({ requirementId, resourceIds })));
    }
  }, [editing, reservation, availability.data, slot, date]);

  const run = async (action: () => Promise<unknown>, success: string) => {
    try {
      await action();
      toast.success(success);
      setEditing(false);
      setCancelling(false);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  const busy = changeStatus.isPending || reschedule.isPending;

  return (
    <Dialog open={id !== null} onClose={busy ? undefined : onClose} maxWidth="md" fullWidth fullScreen={fullScreen}>
      {isLoading || !reservation ? (
        <DialogContent>
          <LoadingState />
        </DialogContent>
      ) : (
        <>
          <DialogTitle>
            <Stack direction="row" spacing={1.5} alignItems="center" justifyContent="space-between">
              <span>
                {reservation.serviceName}
                <Typography component="span" color="text.secondary" sx={{ ml: 1 }}>
                  #{reservation.id}
                </Typography>
              </span>
              <StatusChip status={reservation.status} size="medium" />
            </Stack>
          </DialogTitle>
          <DialogContent dividers>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <Detail label="When">
                  {formatLocalDate(reservation.localStartTime)}, {formatWindow(reservation)}
                  <Typography variant="caption" color="text.secondary" display="block">
                    {reservation.timezone}
                  </Typography>
                </Detail>
                <Detail label="Customer">
                  <Box component={RouterLink} to={`/admin/customers/${reservation.customerId}`} sx={{ color: 'primary.main' }}>
                    {reservation.customerName}
                  </Box>
                  <Typography variant="body2" color="text.secondary">
                    {[reservation.customerEmail, reservation.customerPhone].filter(Boolean).join(' · ')}
                  </Typography>
                </Detail>
                <Detail label="Participants">{pluralize(reservation.numberOfParticipants, 'person', 'people')}</Detail>
                <Detail label="Assigned resources">
                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mt: 0.5 }}>
                    {reservation.resources.map((resource) => (
                      <Chip
                        key={resource.resourceId}
                        size="small"
                        label={`${resource.resourceTypeName ?? 'Resource'}: ${resource.name}`}
                      />
                    ))}
                    {reservation.resources.length === 0 ? '—' : null}
                  </Stack>
                </Detail>
              </Grid>
              <Grid item xs={12} md={6}>
                <Detail label="Price">
                  <PriceLine label="Service" value={formatMoney(reservation.originalPrice, reservation.currency)} />
                  <PriceLine
                    label={reservation.discountCode ? `Discount (${reservation.discountCode})` : 'Discount'}
                    value={`− ${formatMoney(reservation.discountAmount, reservation.currency)}`}
                  />
                  <Divider sx={{ my: 0.5 }} />
                  <PriceLine bold label="Total" value={formatMoney(reservation.finalPrice, reservation.currency)} />
                </Detail>
                <Detail label="Booked">
                  {formatLocalDate(reservation.createdAt)} ·{' '}
                  {reservation.source === 'PUBLIC' ? 'online by the customer' : 'by the business'}
                </Detail>
                {reservation.customerNotes ? <Detail label="Customer notes">{reservation.customerNotes}</Detail> : null}
                {reservation.internalNotes ? <Detail label="Internal notes">{reservation.internalNotes}</Detail> : null}
                {reservation.cancellationReason ? (
                  <Detail label="Cancellation reason">{reservation.cancellationReason}</Detail>
                ) : null}
              </Grid>
            </Grid>

            {editing ? (
              <Box sx={{ mt: 3 }}>
                <Divider sx={{ mb: 2 }} />
                <Typography variant="subtitle1" fontWeight={700} gutterBottom>
                  Change time or resources
                </Typography>
                <Stack spacing={2}>
                  <TextField
                    type="date"
                    label="Date"
                    value={date}
                    onChange={(event) => {
                      setDate(event.target.value);
                      setSlot(null);
                      setSelections([]);
                    }}
                    InputLabelProps={{ shrink: true }}
                    sx={{ maxWidth: 220 }}
                  />
                  {availability.isLoading ? <LoadingState label="Checking availability…" /> : null}
                  {availability.isError ? <Alert severity="warning">{errorMessage(availability.error)}</Alert> : null}
                  <SlotPicker
                    slots={availability.data?.slots}
                    selected={slot ? slot.time.slice(0, 5) : null}
                    onSelect={(next) => {
                      setSlot(next);
                      setSelections([]);
                    }}
                  />
                  {slot ? (
                    <>
                      <Typography variant="body2" color="text.secondary">
                        Pick resources per requirement; an empty requirement gets the best free resource. Clear
                        every selection to keep the current resources wherever they are still free.
                      </Typography>
                      <ResourcePicker requirements={slot.requirements} value={selections} onChange={setSelections} />
                    </>
                  ) : null}
                </Stack>
              </Box>
            ) : null}

            {cancelling ? (
              <Box sx={{ mt: 3 }}>
                <Divider sx={{ mb: 2 }} />
                <Alert severity="warning" sx={{ mb: 2 }}>
                  Cancelling releases the assigned resources and removes the reservation from revenue.
                </Alert>
                <TextField
                  label="Reason (optional)"
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  fullWidth
                />
              </Box>
            ) : null}
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2, flexWrap: 'wrap', gap: 1 }}>
            {editing ? (
              <>
                <Button onClick={() => setEditing(false)} disabled={busy}>
                  Back
                </Button>
                <Button
                  variant="contained"
                  disabled={!slot || busy}
                  onClick={() =>
                    slot &&
                    run(
                      () =>
                        reschedule.mutateAsync({
                          id: reservation.id,
                          startTime: toLocalDateTimeString(date, slot.time),
                          resourceSelections: selections.length
                            ? completeSelections(slot.requirements, selections)
                            : undefined,
                        }),
                      'Reservation updated',
                    )
                  }
                >
                  Save changes
                </Button>
              </>
            ) : cancelling ? (
              <>
                <Button onClick={() => setCancelling(false)} disabled={busy}>
                  Keep it
                </Button>
                <Button
                  variant="contained"
                  color="error"
                  disabled={busy}
                  onClick={() =>
                    run(
                      () => changeStatus.mutateAsync({ id: reservation.id, status: 'CANCELLED', reason }),
                      'Reservation cancelled',
                    )
                  }
                >
                  Cancel reservation
                </Button>
              </>
            ) : (
              <>
                {reservation.status === 'PENDING' || reservation.status === 'CONFIRMED' ? (
                  <>
                    <Button color="error" onClick={() => setCancelling(true)} disabled={busy}>
                      Cancel
                    </Button>
                    <Button onClick={startEditing} disabled={busy}>
                      Change time / resources
                    </Button>
                  </>
                ) : null}
                <Box sx={{ flex: 1 }} />
                <Button onClick={onClose}>Close</Button>
                {reservation.status === 'PENDING' ? (
                  <Button
                    variant="contained"
                    disabled={busy}
                    onClick={() =>
                      run(() => changeStatus.mutateAsync({ id: reservation.id, status: 'CONFIRMED' }), 'Confirmed')
                    }
                  >
                    Confirm
                  </Button>
                ) : null}
                {reservation.status === 'CONFIRMED' ? (
                  <Button
                    variant="contained"
                    color="success"
                    disabled={busy}
                    onClick={() =>
                      run(
                        () => changeStatus.mutateAsync({ id: reservation.id, status: 'COMPLETED' }),
                        'Marked as completed',
                      )
                    }
                  >
                    Mark completed
                  </Button>
                ) : null}
              </>
            )}
          </DialogActions>
        </>
      )}
    </Dialog>
  );
}

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="caption" color="text.secondary" fontWeight={600} display="block">
        {label}
      </Typography>
      <Typography variant="body2" component="div">
        {children}
      </Typography>
    </Box>
  );
}

function PriceLine({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <Stack direction="row" justifyContent="space-between" sx={{ maxWidth: 320 }}>
      <Typography variant="body2" fontWeight={bold ? 700 : 400}>
        {label}
      </Typography>
      <Typography variant="body2" fontWeight={bold ? 700 : 400}>
        {value}
      </Typography>
    </Stack>
  );
}
