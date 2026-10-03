import { createTheme, type Theme } from '@mui/material/styles';
import { theme as baseTheme } from '@/theme';

const cache = new Map<string, Theme>();

/**
 * The customer pages in the business's own colour. MUI derives the hover
 * shade and a readable text colour on top of it, so any accent works.
 */
export function brandTheme(accent?: string | null): Theme {
  const color = accent && /^#[0-9a-fA-F]{6}$/.test(accent) ? accent : baseTheme.palette.primary.main;
  const cached = cache.get(color);
  if (cached) {
    return cached;
  }
  const created = createTheme(baseTheme, {
    palette: { primary: baseTheme.palette.augmentColor({ color: { main: color } }) },
    shape: { borderRadius: 14 },
  });
  cache.set(color, created);
  return created;
}
