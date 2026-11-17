import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import FormHelperText from '@mui/material/FormHelperText';
import Typography from '@mui/material/Typography';
import { alpha } from '@mui/material/styles';
import { BUSINESS_TYPES, businessTypeInfo } from '@/features/onboarding/businessTypes';
import type { BusinessType } from '@/types/api';

interface BusinessTypePickerProps {
  value: BusinessType | undefined;
  onChange: (type: BusinessType) => void;
  error?: string;
}

/**
 * "What kind of business is it?" at sign-up. The choice decides what the
 * business starts with, so each tile says so once it is picked.
 */
export function BusinessTypePicker({ value, onChange, error }: BusinessTypePickerProps) {
  return (
    <Box>
      <Typography id="business-type-label" variant="body2" fontWeight={600} sx={{ mb: 1 }}>
        What kind of business is it?
      </Typography>
      <Box
        role="radiogroup"
        aria-labelledby="business-type-label"
        sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', sm: 'repeat(3, minmax(0, 1fr))' }, gap: 1 }}
      >
        {BUSINESS_TYPES.map((info) => {
          const selected = info.type === value;
          return (
            <ButtonBase
              key={info.type}
              role="radio"
              aria-checked={selected}
              onClick={() => onChange(info.type)}
              sx={(theme) => ({
                justifyContent: 'flex-start',
                gap: 1,
                px: 1.25,
                py: 1,
                minHeight: 48,
                borderRadius: 1.5,
                textAlign: 'left',
                border: `1px solid ${selected ? theme.palette.primary.main : error ? theme.palette.error.main : theme.palette.divider}`,
                bgcolor: selected ? alpha(theme.palette.primary.main, 0.08) : 'transparent',
                color: selected ? 'primary.main' : 'text.primary',
                // "Something else" spans the row, so the grid ends evenly.
                gridColumn: info.type === 'OTHER' ? '1 / -1' : undefined,
                '&:hover': { bgcolor: alpha(theme.palette.primary.main, 0.04) },
                '&.Mui-focusVisible': { outline: `2px solid ${theme.palette.primary.main}`, outlineOffset: 2 },
              })}
            >
              <Box sx={{ display: 'flex', color: selected ? 'primary.main' : 'text.secondary' }}>{info.icon}</Box>
              <Typography variant="body2" fontWeight={selected ? 700 : 500} sx={{ lineHeight: 1.3 }}>
                {info.label}
              </Typography>
            </ButtonBase>
          );
        })}
      </Box>
      <FormHelperText error={Boolean(error)} sx={{ mx: 1.75 }}>
        {error ?? (value ? `Starts with: ${businessTypeInfo(value).hint.toLowerCase()}. You can change everything later.` : 'We set up your booking page to match. You can change everything later.')}
      </FormHelperText>
    </Box>
  );
}
