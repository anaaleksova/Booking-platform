import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { DARK_COLORS, LIGHT_COLORS, contrastRatio, readableOn } from '../theme';
import type { SiteColors } from '../types';

/** Brand colours that read well with white text on buttons. */
const SWATCHES = ['#2f5bea', '#1f6f8b', '#0f9d76', '#3d7a3a', '#b4536a', '#d23f63', '#c2551d', '#7a4fd6', '#111827'];

const FIELDS: Array<{ key: keyof SiteColors; label: string; hint: string }> = [
  { key: 'primary', label: 'Main colour', hint: 'Buttons and highlights' },
  { key: 'accent', label: 'Accent', hint: 'Small lines above headings, stars' },
  { key: 'background', label: 'Page', hint: 'The page background' },
  { key: 'surface', label: 'Cards', hint: 'Cards and every other band' },
  { key: 'text', label: 'Text colour', hint: 'Headings and text' },
  { key: 'secondary', label: 'Footer', hint: 'The dark band at the bottom' },
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
          <Grid item xs={6} sm={4} key={key}>
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

      <Box sx={{ mt: 2, p: 2, borderRadius: 2, bgcolor: colors.background, border: 1, borderColor: 'divider' }} aria-hidden>
        <Box sx={{ p: 2, borderRadius: 2, bgcolor: colors.surface }}>
          <Typography sx={{ color: colors.accent, fontSize: 11, fontWeight: 700, letterSpacing: '0.2em' }}>PREVIEW</Typography>
          <Typography sx={{ color: colors.text, fontWeight: 800, fontSize: 20 }}>Your heading</Typography>
          <Typography sx={{ color: colors.text, opacity: 0.75, fontSize: 14 }}>Some text the way your customers will read it.</Typography>
          <Box sx={{ mt: 1.5, display: 'inline-block', px: 2, py: 0.75, borderRadius: 999, bgcolor: colors.primary, color: readableOn(colors.primary), fontWeight: 700, fontSize: 14 }}>
            Book now
          </Box>
        </Box>
      </Box>

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
