import { alpha, createTheme, type Theme, type ThemeOptions } from '@mui/material/styles';
import { theme as baseTheme } from '@/theme';
import { FONTS } from '@/features/site/fonts';
import {
  BASE_RADIUS,
  CARD_SHADOW,
  DARK_COLORS,
  DEFAULT_TYPOGRAPHY,
  LIGHT_COLORS,
  completeTheme,
  isDark,
  mutedText,
  withAlpha,
} from '@/features/site/theme';
import type { SiteTheme } from '@/features/site/types';
import type { PublicBusiness } from '@/types/api';

const cache = new Map<string, Theme>();

const HEADINGS = ['h1', 'h2', 'h3', 'h4', 'h5', 'h6'] as const;
const TEXTS = ['subtitle1', 'subtitle2', 'body1', 'body2', 'button', 'caption', 'overline'] as const;

/** A variant's size times `scale`; MUI writes them as rem strings. */
function scaled(size: string | number | undefined, scale: number): string | undefined {
  const rem = typeof size === 'number' ? size / 16 : Number.parseFloat(String(size));
  return Number.isFinite(rem) ? `${Math.round(rem * scale * 10000) / 10000}rem` : undefined;
}

/**
 * The type scale with the chosen fonts and text size. The base theme's
 * variants are already resolved, each with its own font and size, so every
 * variant is set here; a top-level font or size alone would not reach them.
 */
function typographyFor({ typography }: SiteTheme): ThemeOptions['typography'] {
  const scale = typography.baseSize / DEFAULT_TYPOGRAPHY.baseSize;
  const headingFont = typography.headingFont !== DEFAULT_TYPOGRAPHY.headingFont ? FONTS[typography.headingFont]?.stack : undefined;
  const bodyFont = typography.bodyFont !== DEFAULT_TYPOGRAPHY.bodyFont ? FONTS[typography.bodyFont]?.stack : undefined;
  const variant = (name: (typeof HEADINGS)[number] | (typeof TEXTS)[number], font: string | undefined) => ({
    ...(font ? { fontFamily: font } : {}),
    ...(scale !== 1 ? { fontSize: scaled(baseTheme.typography[name].fontSize, scale) } : {}),
  });
  return {
    ...(bodyFont ? { fontFamily: bodyFont } : {}),
    // MUI sizes components from `fontSize` (its default 14 is body text at 16px).
    fontSize: (typography.baseSize * 14) / 16,
    ...Object.fromEntries(HEADINGS.map((name) => [name, variant(name, headingFont)])),
    ...Object.fromEntries(TEXTS.map((name) => [name, variant(name, bodyFont)])),
  };
}

/**
 * The theme a business's customer pages are drawn in: its published
 * homepage theme, or — for a response without one — its accent on a light or
 * black background, as before the builder.
 */
export function brandSiteTheme(business?: Pick<PublicBusiness, 'accentColor' | 'darkTheme' | 'theme'>): SiteTheme {
  if (business?.theme) {
    return completeTheme(business.theme);
  }
  const accent = business?.accentColor && /^#[0-9a-fA-F]{6}$/.test(business.accentColor)
    ? business.accentColor
    : baseTheme.palette.primary.main;
  return completeTheme({ colors: { ...(business?.darkTheme ? DARK_COLORS : LIGHT_COLORS), primary: accent, accent } });
}

/**
 * The MUI theme for a site theme. MUI derives the hover shade and a readable
 * text colour on top of the primary, so any colour works. Each choice that
 * is still at its default leaves MUI as it was, so a page nobody restyled
 * looks exactly as before.
 */
export function brandTheme(site: SiteTheme): Theme {
  const key = JSON.stringify(site);
  const cached = cache.get(key);
  if (cached) {
    return cached;
  }
  const { colors, components } = site;
  const dark = isDark(colors);
  const original = colors.text === LIGHT_COLORS.text || colors.text === DARK_COLORS.text;

  const overrides: NonNullable<ThemeOptions['components']> = {};
  if (dark) {
    overrides.MuiPaper = { styleOverrides: { root: { backgroundImage: 'none' }, outlined: { borderColor: 'rgba(255,255,255,0.14)' } } };
    overrides.MuiTableCell = {
      styleOverrides: { head: { fontWeight: 600, color: mutedText(colors), backgroundColor: original ? '#17181b' : colors.surface } },
    };
  }
  if (components.cardStyle !== 'BORDERED' || components.borderWidth !== 1) {
    const outlined =
      components.cardStyle === 'MINIMAL'
        ? { border: 0 }
        : components.cardStyle === 'SHADOW'
          ? { border: 0, boxShadow: CARD_SHADOW[components.shadow] }
          : { borderWidth: components.borderWidth };
    overrides.MuiPaper = {
      styleOverrides: { ...overrides.MuiPaper?.styleOverrides, outlined: { ...(overrides.MuiPaper?.styleOverrides?.outlined as object), ...outlined } },
    };
  }
  if (components.buttonShape !== 'PILL' || components.buttonStyle !== 'FILLED') {
    const primary = colors.primary;
    overrides.MuiButton = {
      styleOverrides: {
        ...(components.buttonShape !== 'PILL' ? { root: { borderRadius: components.buttonShape === 'SQUARE' ? 0 : 10 } } : {}),
        ...(components.buttonStyle === 'OUTLINE'
          ? {
              containedPrimary: {
                backgroundColor: 'transparent',
                color: dark ? colors.text : primary,
                boxShadow: `inset 0 0 0 2px ${primary}`,
                '&:hover': { backgroundColor: alpha(primary, 0.1), boxShadow: `inset 0 0 0 2px ${primary}` },
              },
            }
          : components.buttonStyle === 'GHOST'
            ? {
                containedPrimary: {
                  backgroundColor: 'transparent',
                  color: dark ? colors.text : primary,
                  boxShadow: 'none',
                  textDecoration: 'underline',
                  textUnderlineOffset: 4,
                  '&:hover': { backgroundColor: alpha(primary, 0.08), textDecoration: 'underline' },
                },
              }
            : {}),
      },
    };
  }

  const created = createTheme(baseTheme, {
    palette: {
      mode: dark ? 'dark' : 'light',
      primary: baseTheme.palette.augmentColor({ color: { main: colors.primary } }),
      secondary: baseTheme.palette.augmentColor({ color: { main: colors.secondary } }),
      background: { default: colors.background, paper: colors.surface },
      text: { primary: colors.text, secondary: mutedText(colors) },
      // The original palettes keep their dividers; others take theirs from the text colour.
      ...(original
        ? dark ? { divider: 'rgba(255,255,255,0.14)' } : {}
        : { divider: withAlpha(colors.text, 0.12) }),
    },
    shape: { borderRadius: BASE_RADIUS[components.corners] },
    typography: typographyFor(site),
    // createTheme chokes on `components: undefined`, so only add the key when there is something in it.
    ...(Object.keys(overrides).length ? { components: overrides } : {}),
  });
  cache.set(key, created);
  return created;
}
