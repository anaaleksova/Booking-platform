import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded';
import { useToast } from '@/components/ToastProvider';
import type { IntegrationStatus } from '@/types/api';
import { STATUS_CHIP } from './integrationLabels';

export function copyToClipboard(text: string, toast: ReturnType<typeof useToast>, what: string) {
  navigator.clipboard
    .writeText(text)
    .then(() => toast.success(`${what} copied`))
    .catch(() => toast.error('Could not copy — select the text and copy it manually.'));
}

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

export function CopyField({ label, value }: { label: string; value: string }) {
  const toast = useToast();
  return (
    <TextField
      fullWidth
      size="small"
      label={label}
      value={value}
      sx={{ mb: 1.5 }}
      InputProps={{
        readOnly: true,
        sx: { fontFamily: 'monospace', fontSize: 13 },
        endAdornment: (
          <InputAdornment position="end">
            <Tooltip title="Copy">
              <IconButton edge="end" onClick={() => copyToClipboard(value, toast, label)}>
                <ContentCopyRoundedIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </InputAdornment>
        ),
      }}
    />
  );
}
