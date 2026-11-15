import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import type { IntegrationStatus } from '@/types/api';
import { STATUS_CHIP } from './integrationLabels';

export function StatusTile({ icon, name, status }: { icon: JSX.Element; name: string; status: IntegrationStatus }) {
  const chip = STATUS_CHIP[status];
  return (
    <Paper variant="outlined" sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 1.5 }}>
      <Box sx={{ color: 'text.secondary', display: 'flex' }}>{icon}</Box>
      <Typography variant="subtitle1" fontWeight={700} sx={{ flex: 1 }}>
        {name}
      </Typography>
      <Chip size="small" variant={status === 'CONNECTED' ? 'filled' : 'outlined'} color={chip.color} label={chip.label} />
    </Paper>
  );
}
