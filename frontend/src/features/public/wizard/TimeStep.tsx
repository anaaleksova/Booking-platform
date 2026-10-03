import { useEffect, useMemo, useState } from 'react';
import dayjs from 'dayjs';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import IconButton from '@mui/material/IconButton';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import { errorMessage } from '@/api/client';
import { usePublicAvailability } from '@/features/public/api';
import type { ResourceSelection, Slot } from '@/types/api';
import { formatLocalDate } from '@/utils/format';
import { slotFitsPicks } from './useWizardState';

const DAYS_SHOWN = 14;

/**
 * Step 3: a day, then a time. Only times the server reports as free — and,
 * when the customer picked someone or something, free for that choice — are
 * offered. Tapping a time moves on.
 */
export function TimeStep({
  slug,
  serviceId,
  people,
  selections,
  date,
  time,
  onSelect,
  onDate,
}: {
  slug: string;
  serviceId: number;
  people: number;
  selections: ResourceSelection[];
  date: string | null;
  time: string | null;
  onSelect: (date: string, slot: Slot) => void;
  onDate: (date: string) => void;
}) {
  const today = dayjs().startOf('day');
  const initialStart = date && dayjs(date).isAfter(today.add(DAYS_SHOWN - 1, 'day')) ? dayjs(date).startOf('day') : today;
  const [windowStart, setWindowStart] = useState(initialStart);
  const from = windowStart.format('YYYY-MM-DD');
  const to = windowStart.add(DAYS_SHOWN - 1, 'day').format('YYYY-MM-DD');
  const availability = usePublicAvailability(slug, serviceId, from, to, people);

  const slotsByDay = useMemo(() => {
    const map = new Map<string, Slot[]>();
    (availability.data ?? []).forEach((day) => map.set(day.date, day.slots.filter((slot) => slotFitsPicks(slot, selections))));
    return map;
  }, [availability.data, selections]);

  // Default to the first day that has something free.
  useEffect(() => {
    if (!availability.data || (date && slotsByDay.has(date))) {
      return;
    }
    const first = availability.data.find((day) => (slotsByDay.get(day.date) ?? []).length > 0);
    if (first && !date) {
      onDate(first.date);
    }
  }, [availability.data, date, slotsByDay, onDate]);

  const slots = date ? slotsByDay.get(date) : undefined;
  const pickedSomeone = selections.length > 0;

  return (
    <Stack spacing={3}>
      <Stack direction="row" alignItems="center" spacing={1}>
        <IconButton
          aria-label="Earlier dates"
          disabled={!windowStart.isAfter(today)}
          onClick={() => {
            const earlier = windowStart.subtract(DAYS_SHOWN, 'day');
            setWindowStart(earlier.isBefore(today) ? today : earlier);
          }}
        >
          <ChevronLeftRoundedIcon />
        </IconButton>
        <Box sx={{ display: 'flex', gap: 1, overflowX: 'auto', py: 0.5, flex: 1, scrollbarWidth: 'none' }}>
          {Array.from({ length: DAYS_SHOWN }, (_, index) => windowStart.add(index, 'day')).map((value) => {
            const key = value.format('YYYY-MM-DD');
            const count = slotsByDay.get(key)?.length ?? 0;
            const selected = key === date;
            const loading = availability.isLoading;
            return (
              <ButtonBase
                key={key}
                disabled={!loading && count === 0}
                onClick={() => onDate(key)}
                sx={{
                  flexDirection: 'column',
                  minWidth: 60,
                  py: 1.25,
                  borderRadius: 3,
                  border: 2,
                  borderColor: selected ? 'primary.main' : 'divider',
                  bgcolor: selected ? 'primary.main' : 'background.paper',
                  color: selected ? 'primary.contrastText' : count === 0 && !loading ? 'text.disabled' : 'text.primary',
                }}
              >
                <Typography variant="caption" sx={{ textTransform: 'uppercase', fontWeight: 600 }}>
                  {value.format('ddd')}
                </Typography>
                <Typography variant="h6" fontWeight={800} lineHeight={1.2}>
                  {value.format('D')}
                </Typography>
                <Typography variant="caption">{value.format('MMM')}</Typography>
              </ButtonBase>
            );
          })}
        </Box>
        <IconButton aria-label="Later dates" onClick={() => setWindowStart(windowStart.add(DAYS_SHOWN, 'day'))}>
          <ChevronRightRoundedIcon />
        </IconButton>
      </Stack>

      {availability.isError ? <Alert severity="warning">{errorMessage(availability.error)}</Alert> : null}

      <div>
        <Typography variant="h6" fontWeight={800} sx={{ mb: 1.5 }}>
          {date ? formatLocalDate(date) : 'Choose a day'}
        </Typography>
        {availability.isLoading ? (
          <Stack direction="row" spacing={1}>
            {Array.from({ length: 6 }, (_, index) => (
              <Skeleton key={index} variant="rounded" width={84} height={44} />
            ))}
          </Stack>
        ) : slots && slots.length > 0 ? (
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(84px, 1fr))', gap: 1 }}>
            {slots.map((slot) => {
              const label = slot.time.slice(0, 5);
              return (
                <Button
                  key={slot.startTime}
                  variant={label === time ? 'contained' : 'outlined'}
                  onClick={() => date && onSelect(date, slot)}
                  sx={{ py: 1.25, borderRadius: 2.5, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}
                >
                  {label}
                </Button>
              );
            })}
          </Box>
        ) : date ? (
          <Typography color="text.secondary">
            Nothing free on this day{pickedSomeone ? ' for your choice' : ''}. Try another day
            {pickedSomeone ? ' or go back and choose “No preference”' : ''}.
          </Typography>
        ) : (
          <Typography color="text.secondary">No free times in these two weeks. Try later dates.</Typography>
        )}
      </div>
    </Stack>
  );
}
