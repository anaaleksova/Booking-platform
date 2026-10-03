import dayjs from 'dayjs';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import type { DateRange } from '@/features/analytics/api';

export type RangePreset = '7d' | '30d' | '90d' | 'month' | 'custom';

export function presetRange(preset: Exclude<RangePreset, 'custom'>): DateRange {
  const today = dayjs();
  const to = today.format('YYYY-MM-DD');
  switch (preset) {
    case '7d':
      return { from: today.subtract(6, 'day').format('YYYY-MM-DD'), to };
    case '30d':
      return { from: today.subtract(29, 'day').format('YYYY-MM-DD'), to };
    case '90d':
      return { from: today.subtract(89, 'day').format('YYYY-MM-DD'), to };
    case 'month':
      return { from: today.startOf('month').format('YYYY-MM-DD'), to: today.endOf('month').format('YYYY-MM-DD') };
  }
}

/** The one filter row above a page's charts: quick ranges plus a custom from/to. */
export function DateRangeFilter({
  preset,
  range,
  onChange,
}: {
  preset: RangePreset;
  range: DateRange;
  onChange: (preset: RangePreset, range: DateRange) => void;
}) {
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems={{ sm: 'center' }} useFlexGap flexWrap="wrap">
      <ToggleButtonGroup
        size="small"
        exclusive
        value={preset}
        onChange={(_, value: RangePreset | null) => {
          if (value && value !== 'custom') {
            onChange(value, presetRange(value));
          } else if (value === 'custom') {
            onChange('custom', range);
          }
        }}
      >
        <ToggleButton value="7d">7 days</ToggleButton>
        <ToggleButton value="30d">30 days</ToggleButton>
        <ToggleButton value="90d">90 days</ToggleButton>
        <ToggleButton value="month">This month</ToggleButton>
        <ToggleButton value="custom">Custom</ToggleButton>
      </ToggleButtonGroup>
      {preset === 'custom' ? (
        <Stack direction="row" spacing={1}>
          <TextField
            type="date"
            label="From"
            value={range.from}
            InputLabelProps={{ shrink: true }}
            onChange={(event) => event.target.value && onChange('custom', { ...range, from: event.target.value })}
          />
          <TextField
            type="date"
            label="To"
            value={range.to}
            InputLabelProps={{ shrink: true }}
            onChange={(event) => event.target.value && onChange('custom', { ...range, to: event.target.value })}
          />
        </Stack>
      ) : null}
    </Stack>
  );
}
