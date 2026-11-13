import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { errorMessage } from '@/api/client';
import { LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { ConflictList } from '@/features/schedule/ScheduleConflicts';
import type { ReassignmentResult, ScheduleConflict } from '@/types/api';
import { pluralize } from '@/utils/format';
import { fetchUpcomingReservations, useReassignReservations, useStrandedBookings } from './api';

/** A resource (or employee, through its resource) whose bookings are in question. */
export interface BookingHolder {
  resourceId: number;
  name: string;
}

/**
 * Deactivating a resource that still has bookings: first asks, listing them,
 * then deactivates and — if chosen — gives them to free resources at the
 * same times. Without bookings it deactivates straight away.
 */
export function useDeactivation(deactivate: (holder: BookingHolder) => Promise<unknown>) {
  const toast = useToast();
  const [state, setState] = useState<{ holder: BookingHolder; bookings: ScheduleConflict[] } | null>(null);

  const request = async (holder: BookingHolder) => {
    try {
      const bookings = await fetchUpcomingReservations(holder.resourceId);
      if (bookings.length === 0) {
        await deactivate(holder);
        return;
      }
      setState({ holder, bookings });
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const dialog = state ? (
    <ResourceBookingsDialog
      holder={state.holder}
      initial={state.bookings}
      deactivate={() => deactivate(state.holder)}
      onClose={() => setState(null)}
    />
  ) : null;
  return { request, dialog };
}

/** "3 bookings" on an inactive resource; opens the bookings to move them. Nothing when it has none. */
export function StrandedChip({ holder }: { holder: BookingHolder }) {
  const { data } = useStrandedBookings();
  const [open, setOpen] = useState(false);
  const count = data?.find((row) => row.resourceId === holder.resourceId)?.reservations ?? 0;
  if (count === 0) {
    return null;
  }
  return (
    <>
      <Chip
        size="small"
        color="warning"
        label={`${pluralize(count, 'booking')} to move`}
        onClick={() => setOpen(true)}
        sx={{ mt: 0.5 }}
      />
      {open ? <ResourceBookingsDialog holder={holder} onClose={() => setOpen(false)} /> : null}
    </>
  );
}

/**
 * The bookings a resource still holds. Before deactivating: keep it active,
 * deactivate only, or deactivate and reassign. Once inactive: reassign, or
 * move and cancel bookings one by one.
 */
function ResourceBookingsDialog({
  holder,
  initial,
  deactivate,
  onClose,
}: {
  holder: BookingHolder;
  initial?: ScheduleConflict[];
  /** Set while the resource is still active. */
  deactivate?: () => Promise<unknown>;
  onClose: () => void;
}) {
  const toast = useToast();
  const reassign = useReassignReservations();
  const [bookings, setBookings] = useState<ScheduleConflict[] | null>(initial ?? null);
  const [result, setResult] = useState<ReassignmentResult | null>(null);
  const [busy, setBusy] = useState(false);

  // Opened from the chip: the list is loaded here.
  useEffect(() => {
    if (initial) {
      return;
    }
    fetchUpcomingReservations(holder.resourceId)
      .then(setBookings)
      .catch((err) => toast.error(errorMessage(err)));
  }, [holder.resourceId, initial, toast]);

  const run = async (withReassign: boolean) => {
    setBusy(true);
    try {
      if (deactivate) {
        await deactivate();
      }
      if (withReassign) {
        const outcome = await reassign.mutateAsync(holder.resourceId);
        setResult(outcome);
        if (outcome.failed.length === 0) {
          toast.success(`${pluralize(outcome.reassigned.length, 'booking')} given to others`);
        }
      } else {
        toast.success(`${holder.name} deactivated — its bookings stay until you move them`);
        onClose();
      }
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{result ? 'Bookings reassigned' : `${holder.name} still has bookings`}</DialogTitle>
      <DialogContent>
        {result ? (
          <Stack spacing={2}>
            {result.reassigned.length > 0 ? (
              <>
                <Alert severity="success">
                  {pluralize(result.reassigned.length, 'booking')} moved to others at the same time. Customers keep
                  their time; staff who were given one are told.
                </Alert>
                <ConflictList conflicts={result.reassigned} canResolve={false} reassignable={false} />
              </>
            ) : null}
            {result.failed.length > 0 ? (
              <>
                <Alert severity="warning">
                  Nothing else was free for {pluralize(result.failed.length, 'booking')}. Move each to another time or
                  cancel it.
                </Alert>
                <ConflictList
                  conflicts={result.failed.map((failure) => failure.booking)}
                  canResolve
                  reassignable={false}
                />
              </>
            ) : null}
          </Stack>
        ) : bookings === null ? (
          <LoadingState />
        ) : bookings.length === 0 ? (
          <Typography color="text.secondary">No upcoming bookings left.</Typography>
        ) : (
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              {deactivate
                ? `Once deactivated, ${holder.name} gets no new bookings. These ${pluralize(bookings.length, 'booking')} still hold it:`
                : `${holder.name} is no longer active, but these bookings still hold it:`}
            </Typography>
            <ConflictList conflicts={bookings} canResolve={!deactivate} reassignable={false} />
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2, flexWrap: 'wrap', gap: 1 }}>
        {result ? (
          <Button variant="contained" onClick={onClose}>
            Done
          </Button>
        ) : deactivate ? (
          <>
            <Button onClick={onClose} disabled={busy}>
              Keep active
            </Button>
            <Button onClick={() => run(false)} disabled={busy}>
              Deactivate only
            </Button>
            <Button variant="contained" onClick={() => run(true)} disabled={busy || bookings === null}>
              Deactivate and reassign
            </Button>
          </>
        ) : (
          <>
            <Button onClick={onClose} disabled={busy}>
              Close
            </Button>
            <Button variant="contained" onClick={() => run(true)} disabled={busy || !bookings?.length}>
              Reassign automatically
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
}
