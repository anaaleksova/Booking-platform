import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Slider from '@mui/material/Slider';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { FONTS, FONT_KEYS, loadFonts } from '../fonts';
import type { SiteFont, SiteTypography } from '../types';

const WEIGHTS = [
  { value: 300, label: 'Light' },
  { value: 400, label: 'Regular' },
  { value: 500, label: 'Medium' },
  { value: 600, label: 'Semi-bold' },
  { value: 700, label: 'Bold' },
  { value: 800, label: 'Extra bold' },
  { value: 900, label: 'Black' },
];

/** A font menu, each name written in its own font. Opening it loads them all (the admin only). */
function FontSelect({
  label,
  value,
  onChange,
  forText = false,
}: {
  label: string;
  value: SiteFont;
  onChange: (font: SiteFont) => void;
  forText?: boolean;
}) {
  const options = FONT_KEYS.filter((font) => !(forText && FONTS[font].headingsOnly));
  return (
    <TextField
      select
      label={label}
      value={value}
      onChange={(event) => onChange(event.target.value as SiteFont)}
      SelectProps={{ onOpen: () => void loadFonts(options), renderValue: (font) => FONTS[font as SiteFont].label }}
      helperText={FONTS[value].cyrillic ? `${FONTS[value].kind}` : `${FONTS[value].kind} · Latin letters only: Cyrillic shows in the device font`}
      fullWidth
    >
      {options.map((font) => (
        <MenuItem key={font} value={font}>
          <Stack direction="row" justifyContent="space-between" alignItems="baseline" spacing={2} sx={{ width: '100%' }}>
            <Box component="span" sx={{ fontFamily: FONTS[font].stack, fontSize: 18 }}>
              {FONTS[font].label}
            </Box>
            <Typography variant="caption" color="text.secondary">
              {FONTS[font].kind}
              {FONTS[font].cyrillic ? '' : ' · Latin only'}
            </Typography>
          </Stack>
        </MenuItem>
      ))}
    </TextField>
  );
}

/** Fonts, text size and how headings look, on every customer page. */
export function TypographyCard({
  typography,
  onChange,
}: {
  typography: SiteTypography;
  onChange: (typography: Partial<SiteTypography>) => void;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="subtitle1" fontWeight={700} gutterBottom>
        Fonts
      </Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6}>
          <FontSelect label="Heading font" value={typography.headingFont} onChange={(headingFont) => onChange({ headingFont })} />
        </Grid>
        <Grid item xs={12} sm={6}>
          <FontSelect label="Text font" value={typography.bodyFont} onChange={(bodyFont) => onChange({ bodyFont })} forText />
        </Grid>
        <Grid item xs={12} sm={6}>
          <TextField
            select
            label="Heading weight"
            value={typography.headingWeight}
            onChange={(event) => onChange({ headingWeight: Number(event.target.value) })}
            fullWidth
          >
            {WEIGHTS.map((weight) => (
              <MenuItem key={weight.value} value={weight.value}>
                {weight.label}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid item xs={12} sm={6}>
          <Typography variant="body2" id="text-size-label" gutterBottom>
            Text size: {typography.baseSize}px
          </Typography>
          <Slider
            aria-labelledby="text-size-label"
            value={typography.baseSize}
            min={14}
            max={20}
            step={1}
            marks
            onChange={(_, value) => onChange({ baseSize: value as number })}
          />
        </Grid>
        <Grid item xs={12} sm={6}>
          <Typography variant="body2" gutterBottom id="heading-style-label">
            Heading style
          </Typography>
          <ToggleButtonGroup
            exclusive
            size="small"
            aria-labelledby="heading-style-label"
            value={typography.headingStyle}
            onChange={(_, headingStyle) => headingStyle && onChange({ headingStyle })}
          >
            <ToggleButton value="NORMAL">Normal</ToggleButton>
            <ToggleButton value="UPPERCASE">CAPITALS</ToggleButton>
            <ToggleButton value="ITALIC" sx={{ fontStyle: 'italic' }}>
              Italic
            </ToggleButton>
          </ToggleButtonGroup>
        </Grid>
        <Grid item xs={12} sm={6}>
          <Typography variant="body2" gutterBottom id="heading-align-label">
            Section headings
          </Typography>
          <ToggleButtonGroup
            exclusive
            size="small"
            aria-labelledby="heading-align-label"
            value={typography.headingAlign}
            onChange={(_, headingAlign) => headingAlign && onChange({ headingAlign })}
          >
            <ToggleButton value="CENTER">Centred</ToggleButton>
            <ToggleButton value="LEFT">Left</ToggleButton>
          </ToggleButtonGroup>
        </Grid>
      </Grid>
    </Paper>
  );
}
