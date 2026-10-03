import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import RemoveRoundedIcon from '@mui/icons-material/RemoveRounded';

/** "[-] 2 [+]" — bounded by 1 and the service's maximum. */
export function ParticipantStepper({
  value,
  max,
  onChange,
}: {
  value: number;
  max: number;
  onChange: (value: number) => void;
}) {
  return (
    <Stack direction="row" alignItems="center" spacing={2}>
      <IconButton aria-label="Fewer people" onClick={() => onChange(value - 1)} disabled={value <= 1} sx={{ border: 1, borderColor: 'divider' }}>
        <RemoveRoundedIcon />
      </IconButton>
      <Typography variant="h5" sx={{ minWidth: 32, textAlign: 'center', fontVariantNumeric: 'tabular-nums' }} aria-live="polite">
        {value}
      </Typography>
      <IconButton aria-label="More people" onClick={() => onChange(value + 1)} disabled={value >= max} sx={{ border: 1, borderColor: 'divider' }}>
        <AddRoundedIcon />
      </IconButton>
    </Stack>
  );
}
