import type { SiteColors, SiteComponents, SiteTheme, SiteTypography } from './types';

/** The two palettes every business had before the builder: light, and "black background". */
export const LIGHT_COLORS: SiteColors = {
  primary: '#2f5bea',
  secondary: '#111214',
  accent: '#2f5bea',
  background: '#fafafa',
  surface: '#ffffff',
  text: '#131a2b',
};

export const DARK_COLORS: SiteColors = {
  primary: '#2f5bea',
  secondary: '#111214',
  accent: '#2f5bea',
  background: '#000000',
  surface: '#121214',
  text: '#f4f5f7',
};

/** How every page looked before typography could be chosen. */
export const DEFAULT_TYPOGRAPHY: SiteTypography = {
  headingFont: 'SYSTEM',
  bodyFont: 'SYSTEM',
  baseSize: 16,
  headingWeight: 800,
  headingStyle: 'NORMAL',
  headingAlign: 'CENTER',
};

/** How every page looked before these could be chosen. */
export const DEFAULT_COMPONENTS: SiteComponents = {
  corners: 'ROUNDED',
  buttonShape: 'PILL',
  buttonStyle: 'FILLED',
  cardStyle: 'BORDERED',
  imageShape: 'ROUNDED',
  borderWidth: 1,
  shadow: 'SOFT',
};

/** A theme with any part an older response lacks filled in with the defaults. */
export function completeTheme(theme: Partial<SiteTheme> & { colors: SiteColors }): SiteTheme {
  return {
    colors: theme.colors,
    typography: { ...DEFAULT_TYPOGRAPHY, ...theme.typography },
    components: { ...DEFAULT_COMPONENTS, ...theme.components },
  };
}

/** Card corners; ROUNDED is the 56px the cards have always had. */
export const CARD_RADIUS: Record<SiteComponents['corners'], string> = { SHARP: '0px', SOFT: '12px', ROUNDED: '56px' };

/** The MUI base radius (inputs, chips, the booking flow's cards). */
export const BASE_RADIUS: Record<SiteComponents['corners'], number> = { SHARP: 0, SOFT: 6, ROUNDED: 14 };

/** Buttons drawn with an explicit shape; PILL is what the page's buttons have always been. */
export const BUTTON_RADIUS: Record<SiteComponents['buttonShape'], string> = { SQUARE: '0px', ROUNDED: '10px', PILL: '999px' };

export const CARD_SHADOW: Record<SiteComponents['shadow'], string> = {
  SOFT: '0 6px 24px rgba(15, 23, 42, 0.08)',
  STRONG: '0 18px 48px rgba(15, 23, 42, 0.18)',
};

function channels(hex: string): [number, number, number] {
  const value = Number.parseInt(hex.slice(1), 16);
  return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
}

/** WCAG relative luminance, 0 (black) to 1 (white). */
export function luminance(hex: string): number {
  const [r, g, b] = channels(hex).map((channel) => {
    const c = channel / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** WCAG contrast ratio between two colours: 1 (none) to 21 (black on white). Body text wants 4.5. */
export function contrastRatio(a: string, b: string): number {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (light + 0.05) / (dark + 0.05);
}

/**
 * Light text on a dark page. Judged the way the backend judges it when it
 * tells emails to use the black layout, so the two always agree.
 */
export function isDark(colors: SiteColors): boolean {
  const [r, g, b] = channels(colors.background);
  return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255 < 0.4;
}

/** Black or white, whichever reads better on the colour. */
export function readableOn(hex: string): string {
  return contrastRatio(hex, '#ffffff') >= contrastRatio(hex, '#000000') ? '#ffffff' : '#000000';
}

/** `#rrggbb` at an opacity, as an `rgba()` colour. */
export function withAlpha(hex: string, alpha: number): string {
  const [r, g, b] = channels(hex);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/**
 * The muted text colour. The two original palettes keep their hand-picked
 * greys, so existing pages look exactly as before; any other text colour is
 * softened by transparency.
 */
export function mutedText(colors: SiteColors): string {
  if (colors.text === LIGHT_COLORS.text) {
    return '#5a6478';
  }
  if (colors.text === DARK_COLORS.text) {
    return '#a3a8b4';
  }
  return withAlpha(colors.text, 0.7);
}

/**
 * The theme as CSS custom properties, set once on the page root so every
 * section reads them with `var(--site-…)` instead of being handed values.
 */
export function themeVars({ colors, components, typography }: SiteTheme): Record<string, string> {
  return {
    '--site-radius-card': CARD_RADIUS[components.corners],
    '--site-radius-button': BUTTON_RADIUS[components.buttonShape],
    '--site-heading-align': typography.headingAlign === 'LEFT' ? 'left' : 'center',
    '--site-heading-items': typography.headingAlign === 'LEFT' ? 'flex-start' : 'center',
    '--site-primary': colors.primary,
    '--site-on-primary': readableOn(colors.primary),
    '--site-secondary': colors.secondary,
    '--site-on-secondary': readableOn(colors.secondary),
    '--site-accent': colors.accent,
    '--site-bg': colors.background,
    '--site-surface': colors.surface,
    '--site-text': colors.text,
    '--site-text-muted': mutedText(colors),
    '--site-divider': withAlpha(colors.text, 0.12),
  };
}
