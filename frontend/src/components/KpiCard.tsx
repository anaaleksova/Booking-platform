import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

/** A stat tile: sentence-case label, one prominent value, an optional note. */
export function KpiCard({
  label,
  value,
  note,
  icon,
  loading,
}: {
  label: string;
  value: string;
  note?: string;
  icon?: ReactNode;
  loading?: boolean;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2.5, height: '100%' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
        <Typography variant="body2" color="text.secondary" fontWeight={600}>
          {label}
        </Typography>
        {icon ? <Stack sx={{ color: 'text.disabled', '& svg': { fontSize: 22 } }}>{icon}</Stack> : null}
      </Stack>
      {loading ? (
        <Skeleton width="60%" height={44} />
      ) : (
        <Typography sx={{ fontSize: 30, fontWeight: 700, letterSpacing: '-0.02em', mt: 0.5 }}>{value}</Typography>
      )}
      {note ? (
        <Typography variant="caption" color="text.secondary">
          {note}
        </Typography>
      ) : null}
    </Paper>
  );
}
