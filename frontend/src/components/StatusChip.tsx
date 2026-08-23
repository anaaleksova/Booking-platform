import Chip from '@mui/material/Chip';
import type { BookingStatus } from '@/types/api';
import { STATUS_COLORS, STATUS_LABELS } from '@/utils/format';

export function StatusChip({ status, size = 'small' }: { status: BookingStatus; size?: 'small' | 'medium' }) {
  return <Chip size={size} label={STATUS_LABELS[status]} color={STATUS_COLORS[status]} variant="outlined" />;
}
