import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import { errorMessage } from '@/api/client';
import { LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import {
  fetchBlockedTimeConflicts,
  useBlockedTimes,
  useCreateBlockedTime,
  useDeleteBlockedTime,
  type BlockedTimeInput,
} from '@/features/business/api';
import type { BlockedTime, Resource, ScheduleConflict } from '@/types/api';
import { formatLocalDate, formatLocalTime, pluralize } from '@/utils/format';
import { ConflictList, ConflictPreviewDialog } from './ScheduleConflicts';

const EMPTY = { resourceId: '' as number | '', start: '', end: '', reason: '' };

/**
 * Blocks time: the whole business or one resource for the owner, their own
 * calendar for staff. Bookings already in that time are shown before saving.
 */
export function BlockTimeForm({
  ownerView,
  resources,
  ownResourceId,
}: {
  ownerView: boolean;
  /** The owner's choice of what to block. */
  resources?: Resource[];
  /** Staff block only their own calendar. */
  ownResourceId?: number;
}) {
  const toast = useToast();
  const create = useCreateBlockedTime();
  const [draft, setDraft] = useState(EMPTY);
  const [checking, setChecking] = useState(false);
  const [pending, setPending] = useState<{
    input: BlockedTimeInput;
    conflicts: ScheduleConflict[];
  } | null>(null);

  const input = (): BlockedTimeInput => ({
    resourceId: ownerView ? (draft.resourceId === '' ? null : draft.resourceId) : (ownResourceId ?? null),
    startTime: draft.start.length === 16 ? `${draft.start}:00` : draft.start,
    endTime: draft.end.length === 16 ? `${draft.end}:00` : draft.end,
    reason: draft.reason || undefined,
  });

  const save = (value: BlockedTimeInput) =>
    create
      .mutateAsync(value)
      .then((saved) => {
        const done = ownerView ? 'Time blocked' : 'Time off added';
        toast.success(
          saved.conflicts.length > 0 && ownerView
            ? `${done} — ${pluralize(saved.conflicts.length, 'booking')} to sort out in the list`
            : done,
        );
        setDraft(EMPTY);
        setPending(null);
      })
      .catch((err) => toast.error(errorMessage(err)));

  const submit = () => {
    const value = input();
    setChecking(true);
    fetchBlockedTimeConflicts(value)
      .then((conflicts) => (conflicts.length > 0 ? setPending({ input: value, conflicts }) : save(value)))
      .catch((err) => toast.error(errorMessage(err)))
      .finally(() => setChecking(false));
  };

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="subtitle1" fontWeight={700} gutterBottom>
        {ownerView ? 'Block time' : 'Add time off'}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {ownerView
          ? "Close the whole business (a holiday) or take one resource out — an employee's day off, a room in maintenance."
          : 'No new bookings will be made with you in this time. Your manager is emailed about it.'}
      </Typography>
      <Stack spacing={2}>
        {ownerView ? (
          <TextField
            select
            label="Applies to"
            value={draft.resourceId}
            onChange={(e) =>
              setDraft({
                ...draft,
                resourceId: e.target.value === '' ? '' : Number(e.target.value),
              })
            }
          >
            <MenuItem value="">The whole business</MenuItem>
            {(resources ?? []).map((resource) => (
              <MenuItem key={resource.id} value={resource.id}>
                {resource.name} · {resource.resourceTypeName}
              </MenuItem>
            ))}
          </TextField>
        ) : null}
        <TextField
          type="datetime-local"
          label="From"
          value={draft.start}
          InputLabelProps={{ shrink: true }}
          onChange={(e) => setDraft({ ...draft, start: e.target.value })}
        />
        <TextField
          type="datetime-local"
          label="To"
          value={draft.end}
          InputLabelProps={{ shrink: true }}
          onChange={(e) => setDraft({ ...draft, end: e.target.value })}
        />
        <TextField
          label="Reason"
          value={draft.reason}
          onChange={(e) => setDraft({ ...draft, reason: e.target.value })}
        />
        <Button
          variant="contained"
          disabled={!draft.start || !draft.end || checking || create.isPending}
          onClick={submit}
        >
          {ownerView ? 'Block time' : 'Add time off'}
        </Button>
      </Stack>
      <ConflictPreviewDialog
        open={pending !== null}
        conflicts={pending?.conflicts ?? []}
        confirmLabel={ownerView ? 'Block anyway' : 'Add time off anyway'}
        ownerView={ownerView}
        busy={create.isPending}
        onConfirm={() => pending && save(pending.input)}
        onClose={() => setPending(null)}
      />
    </Paper>
  );
}

/** Upcoming blocks, each with the bookings still inside it. */
export function BlockedTimeList({ ownerView }: { ownerView: boolean }) {
  const toast = useToast();
  const { data: blocks, isLoading } = useBlockedTimes();
  const remove = useDeleteBlockedTime();
  const needAction = (blocks ?? []).filter((block) => block.conflicts.length > 0).length;

  return (
    <Paper variant="outlined">
      <Typography variant="subtitle1" fontWeight={700} sx={{ p: 2, pb: 0 }}>
        Upcoming
      </Typography>
      {ownerView && needAction > 0 ? (
        <Alert severity="warning" sx={{ m: 2, mb: 0 }}>
          {pluralize(needAction, 'block')} still {needAction === 1 ? 'has' : 'have'} bookings in it. Give each to
          someone else, move it or cancel it.
        </Alert>
      ) : null}
      {isLoading ? (
        <LoadingState />
      ) : blocks?.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
          Nothing blocked in the next three months.
        </Typography>
      ) : (
        <Stack divider={<Divider />} sx={{ px: 2, pb: 1 }}>
          {blocks?.map((block) => (
            <BlockRow
              key={block.id}
              block={block}
              ownerView={ownerView}
              onRemove={() => remove.mutateAsync(block.id).catch((err) => toast.error(errorMessage(err)))}
            />
          ))}
        </Stack>
      )}
    </Paper>
  );
}

function BlockRow({ block, ownerView, onRemove }: { block: BlockedTime; ownerView: boolean; onRemove: () => void }) {
  const closure = block.resourceId == null;
  // Staff see the business's closures but cannot remove them.
  const removable = ownerView || !closure;
  return (
    <Stack sx={{ py: 1.5 }} spacing={1}>
      <Stack direction="row" alignItems="flex-start" spacing={1}>
        <Stack sx={{ flex: 1, minWidth: 0 }} spacing={0.5}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
            <Typography variant="body2" fontWeight={600}>
              {closure ? 'Whole business closed' : (block.resourceName ?? 'Time off')}
              {block.reason ? ` — ${block.reason}` : ''}
            </Typography>
            {block.enteredByStaff && ownerView ? <Chip size="small" label="Entered by staff" /> : null}
            {block.conflicts.length > 0 ? (
              <Chip
                size="small"
                color="warning"
                label={`${pluralize(block.conflicts.length, 'booking')} to sort out`}
              />
            ) : null}
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {formatLocalDate(block.localStartTime)} {formatLocalTime(block.localStartTime)} →{' '}
            {formatLocalDate(block.localEndTime)} {formatLocalTime(block.localEndTime)}
          </Typography>
        </Stack>
        {removable ? (
          <IconButton aria-label="Remove" onClick={onRemove}>
            <DeleteOutlineRoundedIcon fontSize="small" />
          </IconButton>
        ) : null}
      </Stack>
      {block.conflicts.length > 0 ? (
        <>
          {!ownerView ? (
            <Typography variant="caption" color="text.secondary">
              Your manager has been told and will move these.
            </Typography>
          ) : null}
          <ConflictList conflicts={block.conflicts} canResolve={ownerView} reassignable={!closure} />
        </>
      ) : null}
    </Stack>
  );
}
