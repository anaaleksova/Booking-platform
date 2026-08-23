import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useAvailability } from '@/features/availability/useAvailability';
import { errorMessage } from '@/api/client';
import type { AvailableSlot } from '@/types/api';

/**
 * The slot grid, shared by manual booking, rescheduling and the public page.
 *
 * <p>Slots always come from the server. The UI never derives a time from a
 * duration and an opening hour — that is the engine's job, and duplicating it
 * here is how the two would drift apart.
 */
export function SlotPicker({
  serviceId,
  date,
  staffId,
  resourceId,
  excludeBookingId,
  selected,
  onSelect,
}: {
  serviceId?: number;
  /** ISO date, e.g. `2026-08-20`. */
  date?: string;
  staffId?: number | null;
  resourceId?: number | null;
  excludeBookingId?: number;
  selected?: AvailableSlot | null;
  onSelect: (slot: AvailableSlot) => void;
}) {
  const { data, isFetching, isError, error } = useAvailability({
    serviceId,
    from: date,
    to: date,
    staffId,
    resourceId,
    excludeBookingId,
  });

  if (!serviceId || !date) {
    return (
      <Typography variant="body2" color="text.secondary">
        Pick a service and a date to see available times.
      </Typography>
    );
  }

  if (isFetching && !data) {
    return (
      <Stack direction="row" flexWrap="wrap" gap={1}>
        {Array.from({ length: 8 }).map((_, index) => (
          <Skeleton key={index} variant="rounded" width={76} height={32} />
        ))}
      </Stack>
    );
  }

  if (isError) {
    return <Alert severity="error">{errorMessage(error)}</Alert>;
  }

  const slots = data?.[0]?.slots ?? [];

  if (slots.length === 0) {
    return (
      <Alert severity="info">
        No times are available on this date. Try another date, another staff member, or check the working
        hours.
      </Alert>
    );
  }

  return (
    <Box>
      <Stack direction="row" flexWrap="wrap" gap={1}>
        {slots.map((slot) => {
          const isSelected = selected?.startDateTime === slot.startDateTime;
          return (
            <Chip
              key={slot.startDateTime}
              label={slot.time.slice(0, 5)}
              clickable
              color={isSelected ? 'primary' : 'default'}
              variant={isSelected ? 'filled' : 'outlined'}
              onClick={() => onSelect(slot)}
            />
          );
        })}
      </Stack>
      <Typography variant="caption" color="text.secondary" sx={{ mt: 1.5, display: 'block' }}>
        {slots.length} time{slots.length === 1 ? '' : 's'} available
      </Typography>
    </Box>
  );
}
