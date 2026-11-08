import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Slider from '@mui/material/Slider';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import type { SiteComponents } from '../types';

interface Choice<K extends keyof SiteComponents> {
  key: K;
  label: string;
  options: Array<{ value: SiteComponents[K]; label: string }>;
}

const CHOICES = [
  { key: 'corners', label: 'Card corners', options: [{ value: 'SHARP', label: 'Sharp' }, { value: 'SOFT', label: 'Soft' }, { value: 'ROUNDED', label: 'Round' }] },
  { key: 'buttonShape', label: 'Button shape', options: [{ value: 'SQUARE', label: 'Square' }, { value: 'ROUNDED', label: 'Rounded' }, { value: 'PILL', label: 'Pill' }] },
  { key: 'buttonStyle', label: 'Main buttons', options: [{ value: 'FILLED', label: 'Filled' }, { value: 'OUTLINE', label: 'Outline' }, { value: 'GHOST', label: 'Text only' }] },
  { key: 'cardStyle', label: 'Cards', options: [{ value: 'MINIMAL', label: 'Flat' }, { value: 'BORDERED', label: 'Border' }, { value: 'SHADOW', label: 'Shadow' }] },
  { key: 'imageShape', label: 'Photos', options: [{ value: 'SQUARE', label: 'Square' }, { value: 'ROUNDED', label: 'Rounded' }, { value: 'CIRCLE', label: 'Circle' }] },
] as const satisfies ReadonlyArray<Choice<keyof SiteComponents>>;

/** The shapes of things on every customer page: corners, buttons, cards and photos. */
export function StyleCard({ components, onChange }: { components: SiteComponents; onChange: (components: Partial<SiteComponents>) => void }) {
  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="subtitle1" fontWeight={700} gutterBottom>
        Shapes and style
      </Typography>
      <Grid container spacing={2.5}>
        {CHOICES.map((choice) => (
          <Grid item xs={12} sm={6} key={choice.key}>
            <Typography variant="body2" gutterBottom id={`style-${choice.key}`}>
              {choice.label}
            </Typography>
            <ToggleButtonGroup
              exclusive
              size="small"
              aria-labelledby={`style-${choice.key}`}
              value={components[choice.key]}
              onChange={(_, value) => value && onChange({ [choice.key]: value })}
            >
              {choice.options.map((option) => (
                <ToggleButton key={option.value} value={option.value}>
                  {option.label}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
          </Grid>
        ))}
        {components.cardStyle === 'BORDERED' ? (
          <Grid item xs={12} sm={6}>
            <Typography variant="body2" gutterBottom id="style-border">
              Border: {components.borderWidth}px
            </Typography>
            <Box sx={{ maxWidth: 220 }}>
              <Slider aria-labelledby="style-border" value={components.borderWidth} min={1} max={3} step={1} marks
                onChange={(_, value) => onChange({ borderWidth: value as number })} />
            </Box>
          </Grid>
        ) : null}
        {components.cardStyle === 'SHADOW' ? (
          <Grid item xs={12} sm={6}>
            <Typography variant="body2" gutterBottom id="style-shadow">
              Shadow
            </Typography>
            <ToggleButtonGroup exclusive size="small" aria-labelledby="style-shadow" value={components.shadow}
              onChange={(_, shadow) => shadow && onChange({ shadow })}>
              <ToggleButton value="SOFT">Soft</ToggleButton>
              <ToggleButton value="STRONG">Strong</ToggleButton>
            </ToggleButtonGroup>
          </Grid>
        ) : null}
      </Grid>
    </Paper>
  );
}
