import { useState } from 'react';
import dayjs from 'dayjs';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { useToast } from '@/components/ToastProvider';
import { useCancelInvitation, useInviteEmployee, useRemoveAccess, type StaffAccess } from '@/features/staff/api';
import type { Employee } from '@/types/api';

/**
 * The "Login" line of an employee card: whether they can log in, and the one
 * action that fits — invite, resend or withdraw, or remove access.
 */
export function StaffAccessControl({ employee, access }: { employee: Employee; access?: StaffAccess }) {
  const toast = useToast();
  const invite = useInviteEmployee();
  const cancel = useCancelInvitation();
  const remove = useRemoveAccess();
  const [confirmingRemove, setConfirmingRemove] = useState(false);
  const state = access?.state ?? 'NONE';
  const busy = invite.isPending || cancel.isPending || remove.isPending;

  const sendInvite = () =>
    invite
      .mutateAsync(employee.id)
      .then((result) => toast.success(`Invitation sent to ${result.email}`))
      .catch((err) => toast.error(errorMessage(err)));

  return (
    <>
      <Typography variant="caption" color="text.secondary" fontWeight={600} display="block" sx={{ mt: 1.5 }}>
        Login
      </Typography>
      <Stack direction="row" alignItems="center" spacing={1} useFlexGap flexWrap="wrap" sx={{ mt: 0.5 }}>
        {state === 'ACTIVE' ? (
          <>
            <Chip size="small" color="success" label="Can log in" />
            <Button size="small" color="error" disabled={busy} onClick={() => setConfirmingRemove(true)}>
              Remove access
            </Button>
          </>
        ) : state === 'INVITED' ? (
          <>
            <Chip size="small" color="warning" label={`Invited · until ${dayjs(access?.expiresAt).format('D MMM')}`} />
            <Button size="small" disabled={busy} onClick={sendInvite}>
              Resend
            </Button>
            <Button
              size="small"
              color="inherit"
              disabled={busy}
              onClick={() =>
                cancel
                  .mutateAsync(employee.id)
                  .then(() => toast.success('Invitation withdrawn'))
                  .catch((err) => toast.error(errorMessage(err)))
              }
            >
              Withdraw
            </Button>
          </>
        ) : (
          <>
            {state === 'EXPIRED' ? <Chip size="small" label="Invitation expired" /> : null}
            {employee.email ? (
              <Button size="small" variant="outlined" disabled={busy} onClick={sendInvite}>
                {state === 'EXPIRED' ? 'Invite again' : 'Invite to log in'}
              </Button>
            ) : (
              <Typography variant="body2" color="text.secondary">
                Add an email to invite them
              </Typography>
            )}
          </>
        )}
      </Stack>

      <ConfirmDialog
        open={confirmingRemove}
        title={`Remove ${employee.name}'s access?`}
        message="They are signed out on their next action and can no longer see reservations. You can invite them again later."
        confirmLabel="Remove access"
        busy={remove.isPending}
        onConfirm={() =>
          remove
            .mutateAsync(employee.id)
            .then(() => toast.success(`${employee.name} can no longer log in`))
            .catch((err) => toast.error(errorMessage(err)))
            .finally(() => setConfirmingRemove(false))
        }
        onClose={() => setConfirmingRemove(false)}
      />
    </>
  );
}
