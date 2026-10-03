import Chip from '@mui/material/Chip';
import type { ReservationStatus } from '@/types/api';
import { STATUS_COLORS, STATUS_LABELS } from '@/utils/format';

/** Status always carries its label, never color alone. */
export function StatusChip({ status, size = 'small' }: { status: ReservationStatus; size?: 'small' | 'medium' }) {
  return <Chip size={size} label={STATUS_LABELS[status]} color={STATUS_COLORS[status]} variant="outlined" />;
}
