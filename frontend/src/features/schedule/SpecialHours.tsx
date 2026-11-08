import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import { errorMessage } from '@/api/client';
import { LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import {
  fetchSpecialDayConflicts,
  useDeleteSpecialDay,
  useSaveSpecialDay,
  useSpecialHours,
  type SpecialDayInput,
} from '@/features/business/api';
import type { ScheduleConflict, SpecialDay, TimeSpan } from '@/types/api';
import { describeSpecialHours, formatLocalDate, pluralize } from '@/utils/format';
import { ConflictList, ConflictPreviewDialog } from './ScheduleConflicts';

const DEFAULT_SPAN: TimeSpan = { startTime: '10:00', endTime: '14:00' };

/**
 * Dates whose hours replace the weekly ones: a holiday, a short day, an extra
 * open Sunday. Bookings the new hours leave outside are shown before saving
 * and stay listed until the owner moves or cancels them.
 */
export function SpecialHoursEditor() {
  const toast = useToast();
  const { data: days, isLoading } = useSpecialHours();
  const saveDay = useSaveSpecialDay();
  const removeDay = useDeleteSpecialDay();
  const [date, setDate] = useState('');
  const [open, setOpen] = useState(false);
  const [spans, setSpans] = useState<TimeSpan[]>([DEFAULT_SPAN]);
  const [note, setNote] = useState('');
  const [checking, setChecking] = useState(false);
  const [pending, setPending] = useState<{
    input: SpecialDayInput;
    conflicts: ScheduleConflict[];
  } | null>(null);

  const reset = () => {
    setDate('');
    setOpen(false);
    setSpans([DEFAULT_SPAN]);
    setNote('');
    setPending(null);
  };

  const save = (input: SpecialDayInput) =>
    saveDay
      .mutateAsync(input)
      .then((saved) => {
        toast.success(
          saved.conflicts.length > 0
            ? `Special hours saved — ${pluralize(saved.conflicts.length, 'booking')} to sort out below`
            : 'Special hours saved',
        );
        reset();
      })
      .catch((err) => toast.error(errorMessage(err)));

  const submit = () => {
    const input: SpecialDayInput = {
      date,
      hours: open ? spans : [],
      note: note || undefined,
    };
    setChecking(true);
    fetchSpecialDayConflicts(input)
      .then((conflicts) => (conflicts.length > 0 ? setPending({ input, conflicts }) : save(input)))
      .catch((err) => toast.error(errorMessage(err)))
      .finally(() => setChecking(false));
  };

  const setSpan = (index: number, patch: Partial<TimeSpan>) =>
    setSpans(spans.map((span, i) => (i === index ? { ...span, ...patch } : span)));

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="subtitle1" fontWeight={700} gutterBottom>
        Special dates
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Different hours on one date — a holiday, a short day before one, or opening on a day you are usually closed.
        They replace the weekly hours for that date only.
      </Typography>

      <Stack spacing={2} sx={{ mb: 3 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField
            type="date"
            label="Date"
            value={date}
            InputLabelProps={{ shrink: true }}
            onChange={(e) => setDate(e.target.value)}
            sx={{ minWidth: 180 }}
          />
          <ToggleButtonGroup
            exclusive
            size="small"
            value={open ? 'open' : 'closed'}
            onChange={(_, value) => value && setOpen(value === 'open')}
          >
            <ToggleButton value="closed">Closed all day</ToggleButton>
            <ToggleButton value="open">Open</ToggleButton>
          </ToggleButtonGroup>
        </Stack>
        {open ? (
          <Stack spacing={1}>
            {spans.map((span, index) => (
              <Stack key={index} direction="row" spacing={1} alignItems="center">
                <TextField
                  type="time"
                  size="small"
                  label="From"
                  value={span.startTime}
                  InputLabelProps={{ shrink: true }}
                  onChange={(e) => setSpan(index, { startTime: e.target.value })}
                />
                <TextField
                  type="time"
                  size="small"
                  label="To"
                  value={span.endTime}
                  InputLabelProps={{ shrink: true }}
                  onChange={(e) => setSpan(index, { endTime: e.target.value })}
                />
                {spans.length > 1 ? (
                  <IconButton
                    aria-label="Remove interval"
                    size="small"
                    onClick={() => setSpans(spans.filter((_, i) => i !== index))}
                  >
                    <CloseRoundedIcon fontSize="small" />
                  </IconButton>
                ) : null}
              </Stack>
            ))}
            <Button
              size="small"
              startIcon={<AddRoundedIcon />}
              sx={{ alignSelf: 'flex-start' }}
              onClick={() => setSpans([...spans, { startTime: '16:00', endTime: '18:00' }])}
            >
              Add a break
            </Button>
          </Stack>
        ) : null}
        <TextField
          label="Note (shown to customers)"
          placeholder="e.g. Christmas Eve"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          inputProps={{ maxLength: 255 }}
        />
        <Button
          variant="contained"
          sx={{ alignSelf: 'flex-start' }}
          disabled={!date || checking || saveDay.isPending}
          onClick={submit}
        >
          Save special date
        </Button>
      </Stack>

      {isLoading ? (
        <LoadingState />
      ) : days?.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No special dates coming up.
        </Typography>
      ) : (
        <Stack divider={<Divider />}>
          {days?.map((day) => (
            <SpecialDayRow
              key={day.date}
              day={day}
              onRemove={() =>
                removeDay
                  .mutateAsync(day.date)
                  .then(() => toast.success('Back to the weekly hours on that date'))
                  .catch((err) => toast.error(errorMessage(err)))
              }
            />
          ))}
        </Stack>
      )}

      <ConflictPreviewDialog
        open={pending !== null}
        conflicts={pending?.conflicts ?? []}
        confirmLabel="Save anyway"
        ownerView
        busy={saveDay.isPending}
        onConfirm={() => pending && save(pending.input)}
        onClose={() => setPending(null)}
      />
    </Paper>
  );
}

function SpecialDayRow({ day, onRemove }: { day: SpecialDay; onRemove: () => void }) {
  return (
    <Stack sx={{ py: 1.5 }} spacing={1}>
      <Stack direction="row" alignItems="flex-start" spacing={1}>
        <Stack sx={{ flex: 1, minWidth: 0 }} spacing={0.5}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
            <Typography variant="body2" fontWeight={600}>
              {formatLocalDate(day.date)}
              {day.note ? ` — ${day.note}` : ''}
            </Typography>
            {day.conflicts.length > 0 ? (
              <Chip
                size="small"
                color="warning"
                label={`${pluralize(day.conflicts.length, 'booking')} outside these hours`}
              />
            ) : null}
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {describeSpecialHours(day)}
          </Typography>
        </Stack>
        <IconButton aria-label="Remove special date" onClick={onRemove}>
          <DeleteOutlineRoundedIcon fontSize="small" />
        </IconButton>
      </Stack>
      {day.conflicts.length > 0 ? (
        <>
          <Alert severity="warning" sx={{ py: 0 }}>
            Move or cancel these bookings: they fall outside the hours for this date.
          </Alert>
          <ConflictList conflicts={day.conflicts} canResolve reassignable={false} />
        </>
      ) : null}
    </Stack>
  );
}
