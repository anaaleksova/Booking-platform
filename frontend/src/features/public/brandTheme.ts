import { createTheme, type Theme } from '@mui/material/styles';
import { theme as baseTheme } from '@/theme';

const cache = new Map<string, Theme>();

/**
 * The customer pages in the business's own colour, on a light or black
 * background. MUI derives the hover shade and a readable text colour on top of
 * the accent, so any accent works.
 */
export function brandTheme(accent?: string | null, dark = false): Theme {
  const color = accent && /^#[0-9a-fA-F]{6}$/.test(accent) ? accent : baseTheme.palette.primary.main;
  const key = `${color}|${dark}`;
  const cached = cache.get(key);
  if (cached) {
    return cached;
  }
  const created = createTheme(baseTheme, {
    palette: {
      mode: dark ? 'dark' : 'light',
      primary: baseTheme.palette.augmentColor({ color: { main: color } }),
      background: dark ? { default: '#000000', paper: '#121214' } : { default: '#fafafa', paper: '#ffffff' },
      ...(dark ? { divider: 'rgba(255,255,255,0.14)', text: { primary: '#f4f5f7', secondary: '#a3a8b4' } } : {}),
    },
    shape: { borderRadius: 14 },
    // createTheme chokes on `components: undefined`, so only add the key in dark mode.
    ...(dark
      ? {
          components: {
            MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' }, outlined: { borderColor: 'rgba(255,255,255,0.14)' } } },
            MuiTableCell: { styleOverrides: { head: { fontWeight: 600, color: '#a3a8b4', backgroundColor: '#17181b' } } },
          },
        }
      : {}),
  });
  cache.set(key, created);
  return created;
}
