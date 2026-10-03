import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { Slot } from '@/types/api';

/**
 * Available start times as buttons. The slots come from the server's
 * availability engine — the UI never decides on its own what is free.
 */
export function SlotPicker({
  slots,
  selected,
  onSelect,
  emptyMessage = 'No times are available on this day.',
}: {
  slots: Slot[] | undefined;
  selected: string | null;
  onSelect: (slot: Slot) => void;
  emptyMessage?: string;
}) {
  if (!slots) {
    return null;
  }
  if (slots.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
        {emptyMessage}
      </Typography>
    );
  }
  return (
    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
      {slots.map((slot) => {
        const time = slot.time.slice(0, 5);
        return (
          <Button
            key={slot.startTime}
            size="small"
            variant={selected === time ? 'contained' : 'outlined'}
            onClick={() => onSelect(slot)}
            sx={{ minWidth: 72, fontVariantNumeric: 'tabular-nums' }}
          >
            {time}
          </Button>
        );
      })}
    </Stack>
  );
}
