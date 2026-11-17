import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { DARK_COLORS, LIGHT_COLORS, contrastRatio, readableOn, withAlpha } from '../theme';
import type { SiteColors } from '../types';

/** Brand colours that read well with white text on buttons. */
const SWATCHES = ['#2f5bea', '#1f6f8b', '#0f9d76', '#3d7a3a', '#b4536a', '#d23f63', '#c2551d', '#7a4fd6', '#111827'];

/** Each hint names exactly what the colour paints, using the section background names. */
const FIELDS: Array<{ key: keyof SiteColors; label: string; hint: string }> = [
  { key: 'primary', label: 'Main colour', hint: 'Buttons, and sections set to “Main colour”' },
  { key: 'accent', label: 'Accent', hint: 'The small line above headings, stars' },
  { key: 'background', label: 'Page', hint: 'Behind sections set to “Page colour”' },
  { key: 'surface', label: 'Cards', hint: 'Cards, the top bar, and sections set to “Card colour”' },
  { key: 'text', label: 'Text colour', hint: 'Headings and text' },
  { key: 'secondary', label: 'Footer', hint: 'The footer, and sections set to “Footer colour”' },
];

/** WCAG AA for body text. */
const MIN_CONTRAST = 4.5;

/** The pairs of colours that are read on each other, and what the owner sees if they are too close. */
export function contrastWarnings(colors: SiteColors): string[] {
  const warnings: string[] = [];
  if (contrastRatio(colors.text, colors.background) < MIN_CONTRAST) {
    warnings.push('The text colour is hard to read on the page colour.');
  }
  if (contrastRatio(colors.text, colors.surface) < MIN_CONTRAST) {
    warnings.push('The text colour is hard to read on the card colour.');
  }
  if (contrastRatio(colors.primary, readableOn(colors.primary)) < 3) {
    warnings.push('Button labels will be hard to read on the main colour.');
  }
  return warnings;
}

/**
 * The colours every customer page is drawn in: the homepage, the booking
 * flow and the reservation page.
 */
export function ThemeCard({ colors, onChange }: { colors: SiteColors; onChange: (colors: Partial<SiteColors>) => void }) {
  const warnings = contrastWarnings(colors);
  /** The colour being pointed at or edited: its parts are outlined in the preview. */
  const [active, setActive] = useState<keyof SiteColors | null>(null);
  // Keeps the business's own colours when switching between light and dark.
  const applyPalette = (palette: SiteColors) =>
    onChange({ background: palette.background, surface: palette.surface, text: palette.text, secondary: palette.secondary });

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="subtitle1" fontWeight={700} gutterBottom>
        Colours
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Used on your page, while booking and in your emails.
      </Typography>

      <Typography variant="subtitle2" gutterBottom>
        Main colour
      </Typography>
      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center" sx={{ mb: 2.5 }}>
        {SWATCHES.map((color) => (
          <ButtonBase
            key={color}
            aria-label={`Use colour ${color}`}
            aria-pressed={colors.primary.toLowerCase() === color}
            onClick={() => onChange({ primary: color, accent: color })}
            sx={{
              width: 36,
              height: 36,
              borderRadius: '50%',
              bgcolor: color,
              outline: colors.primary.toLowerCase() === color ? '3px solid' : 'none',
              outlineColor: 'text.primary',
              outlineOffset: 2,
            }}
          />
        ))}
      </Stack>

      <Stack direction="row" spacing={1} sx={{ mb: 2.5 }}>
        <Button variant="outlined" size="small" onClick={() => applyPalette(LIGHT_COLORS)}>
          Light page
        </Button>
        <Button variant="outlined" size="small" onClick={() => applyPalette(DARK_COLORS)}>
          Black page
        </Button>
      </Stack>

      <Grid container spacing={2}>
        {FIELDS.map(({ key, label, hint }) => (
          <Grid
            item
            xs={6}
            sm={4}
            key={key}
            onMouseEnter={() => setActive(key)}
            onMouseLeave={() => setActive(null)}
            onFocus={() => setActive(key)}
            onBlur={() => setActive(null)}
          >
            <TextField
              type="color"
              label={label}
              value={colors[key]}
              onChange={(event) => onChange({ [key]: event.target.value })}
              helperText={hint}
              fullWidth
            />
          </Grid>
        ))}
      </Grid>

      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2, mb: 1 }}>
        {active
          ? `Outlined: what “${FIELDS.find((field) => field.key === active)?.label}” paints.`
          : 'Point at a colour above to see where it is used.'}
      </Typography>
      <ColorMap colors={colors} active={active} />

      {warnings.length ? (
        <Alert severity="warning" sx={{ mt: 2 }}>
          {warnings.map((warning) => (
            <div key={warning}>{warning}</div>
          ))}
        </Alert>
      ) : null}
    </Paper>
  );
}

/**
 * A small sketch of a page, painted in the chosen colours: top bar, a section
 * on the page colour with a card, a band in the main colour and the footer.
 * The parts the active colour paints are outlined.
 */
function ColorMap({ colors, active }: { colors: SiteColors; active: keyof SiteColors | null }) {
  const mark = (key: keyof SiteColors, inset = false) =>
    active === key
      ? { outline: '2px dashed', outlineColor: 'warning.main', outlineOffset: inset ? -4 : 2, position: 'relative' as const, zIndex: 1 }
      : {};
  const button = (label: string) => (
    <Box
      component="span"
      sx={{ display: 'inline-block', px: 1.5, py: 0.5, borderRadius: 999, bgcolor: colors.primary, color: readableOn(colors.primary), fontWeight: 700, fontSize: 12, ...mark('primary') }}
    >
      {label}
    </Box>
  );

  return (
    <Box aria-hidden sx={{ borderRadius: 2, border: 1, borderColor: 'divider', overflow: 'hidden', fontSize: 12 }}>
      {/* Top bar */}
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ px: 1.5, py: 1, bgcolor: colors.surface, borderBottom: `1px solid ${withAlpha(colors.text, 0.12)}`, ...mark('surface', true) }}>
        <Typography sx={{ color: colors.text, fontWeight: 800, fontSize: 13, ...mark('text') }}>Your business</Typography>
        {button('Book now')}
      </Stack>

      {/* A section on the page colour, with a card */}
      <Box sx={{ p: 2, bgcolor: colors.background, ...mark('background', true) }}>
        <Box sx={{ width: 24, height: 3, borderRadius: 2, bgcolor: colors.accent, mb: 0.75, ...mark('accent') }} />
        <Typography sx={{ color: colors.text, fontWeight: 800, fontSize: 16, display: 'inline-block', ...mark('text') }}>Our services</Typography>
        <Stack direction="row" spacing={1.5} sx={{ mt: 1.25 }}>
          <Box sx={{ flex: 1, p: 1.25, borderRadius: 2, bgcolor: colors.surface, border: `1px solid ${withAlpha(colors.text, 0.12)}`, ...mark('surface') }}>
            <Typography sx={{ color: colors.text, fontWeight: 700, fontSize: 13, display: 'inline-block', ...mark('text') }}>Manicure</Typography>
            <Typography sx={{ color: colors.text, opacity: 0.7, fontSize: 12 }}>45 min · a card</Typography>
            <Box sx={{ mt: 0.5, color: colors.accent, letterSpacing: 1, display: 'inline-block', ...mark('accent') }}>★★★★★</Box>
          </Box>
          <Stack spacing={1} alignItems="flex-start" justifyContent="center" sx={{ flex: 1 }}>
            <Typography sx={{ color: colors.text, opacity: 0.75, fontSize: 12 }}>Text right on the page.</Typography>
            {button('Book')}
          </Stack>
        </Stack>
      </Box>

      {/* A section set to the main colour */}
      <Box sx={{ px: 2, py: 1.25, bgcolor: colors.primary, color: readableOn(colors.primary), fontWeight: 700, ...mark('primary', true) }}>
        A section set to “Main colour”
      </Box>

      {/* Footer */}
      <Box sx={{ px: 2, py: 1.25, bgcolor: colors.secondary, color: withAlpha(readableOn(colors.secondary), 0.75), textAlign: 'center', ...mark('secondary', true) }}>
        Footer · address · opening hours
      </Box>
    </Box>
  );
}
