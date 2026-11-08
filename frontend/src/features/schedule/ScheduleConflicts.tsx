import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import { errorMessage } from '@/api/client';
import { useToast } from '@/components/ToastProvider';
import { ReservationDetailsDialog } from '@/features/reservations/ReservationDetailsDialog';
import { useRescheduleReservation } from '@/features/reservations/api';
import type { ScheduleConflict } from '@/types/api';
import { formatLocalDate, formatWindow, pluralize } from '@/utils/format';

function describe(conflict: ScheduleConflict) {
  return {
    primary: `${formatLocalDate(conflict.localStartTime)} · ${formatWindow(conflict)}`,
    secondary: [conflict.serviceName, conflict.customerName, conflict.resources.join(', ')].filter(Boolean).join(' · '),
  };
}

/**
 * Bookings stranded by time off, a closure or special hours, each with what
 * the owner can do about it. "Give to someone else" keeps the time and lets
 * the server pick a free resource in place of the blocked one; "Move or
 * cancel" opens the booking itself.
 */
export function ConflictList({
  conflicts,
  canResolve,
  reassignable,
}: {
  conflicts: ScheduleConflict[];
  /** Owners resolve clashes; staff only see them. */
  canResolve: boolean;
  /** The clash is with one resource (time off), so another one may take the booking. */
  reassignable: boolean;
}) {
  const toast = useToast();
  const reschedule = useRescheduleReservation();
  const [openId, setOpenId] = useState<number | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const reassign = (conflict: ScheduleConflict) => {
    setBusyId(conflict.reservationId);
    reschedule
      .mutateAsync({
        id: conflict.reservationId,
        startTime: conflict.localStartTime,
      })
      .then(() => toast.success('Booking given to someone free at that time'))
      .catch((err) => toast.error(errorMessage(err)))
      .finally(() => setBusyId(null));
  };

  return (
    <>
      <List dense disablePadding>
        {conflicts.map((conflict) => {
          const { primary, secondary } = describe(conflict);
          return (
            <ListItem key={conflict.reservationId} disableGutters sx={{ flexWrap: 'wrap', columnGap: 1 }}>
              <ListItemText primary={primary} secondary={secondary} sx={{ flex: '1 1 220px' }} />
              {canResolve ? (
                <Stack direction="row" spacing={1}>
                  {reassignable ? (
                    <Button size="small" disabled={busyId !== null} onClick={() => reassign(conflict)}>
                      Give to someone else
                    </Button>
                  ) : null}
                  <Button size="small" variant="outlined" onClick={() => setOpenId(conflict.reservationId)}>
                    Move or cancel
                  </Button>
                </Stack>
              ) : null}
            </ListItem>
          );
        })}
      </List>
      {canResolve ? <ReservationDetailsDialog id={openId} onClose={() => setOpenId(null)} /> : null}
    </>
  );
}

/** Shown before saving a change that would strand bookings: what is in the way, and go ahead or not. */
export function ConflictPreviewDialog({
  open,
  conflicts,
  confirmLabel,
  ownerView,
  busy,
  onConfirm,
  onClose,
}: {
  open: boolean;
  conflicts: ScheduleConflict[];
  confirmLabel: string;
  /** The owner sorts the bookings out afterwards; staff hand them to the owner. */
  ownerView: boolean;
  busy: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{pluralize(conflicts.length, 'booking')} in that time</DialogTitle>
      <DialogContent>
        <Alert severity="warning" sx={{ mb: 1 }}>
          {ownerView
            ? 'Saving keeps these bookings as they are, but no new ones will be made. Afterwards you can give each one to someone else, move it or cancel it.'
            : 'Your manager will be emailed to give these bookings to someone else or move them. No new bookings will be made with you in that time.'}
        </Alert>
        <ConflictList conflicts={conflicts} canResolve={false} reassignable={false} />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={busy}>
          Back
        </Button>
        <Button variant="contained" color="warning" onClick={onConfirm} disabled={busy}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
