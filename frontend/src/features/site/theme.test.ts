import { describe, expect, it } from 'vitest';
import { theme as baseTheme } from '@/theme';
import { brandSiteTheme, brandTheme } from '@/features/public/brandTheme';
import { FONTS, FONT_KEYS } from './fonts';
import { DEFAULT_COMPONENTS, DEFAULT_TYPOGRAPHY, LIGHT_COLORS, completeTheme, contrastRatio, themeVars } from './theme';
import type { SiteComponents, SiteTheme, SiteTypography } from './types';

const defaults = completeTheme({ colors: LIGHT_COLORS });
const styled = (theme: { typography?: Partial<SiteTypography>; components?: Partial<SiteComponents> }): SiteTheme => ({
  colors: LIGHT_COLORS,
  typography: { ...DEFAULT_TYPOGRAPHY, ...theme.typography },
  components: { ...DEFAULT_COMPONENTS, ...theme.components },
});

describe('site theme', () => {
  it('leaves the look of an unstyled page exactly as it was', () => {
    const mui = brandTheme(defaults);

    expect(mui.typography.fontFamily).toBe(baseTheme.typography.fontFamily);
    expect(mui.typography.h2.fontFamily).toBe(baseTheme.typography.fontFamily);
    expect(mui.typography.body1).toEqual(baseTheme.typography.body1);
    expect(mui.typography.button).toEqual(baseTheme.typography.button);
    expect(mui.typography.fontSize).toBe(14);
    expect(mui.shape.borderRadius).toBe(14);
    expect(mui.components?.MuiButton?.styleOverrides).toEqual(baseTheme.components?.MuiButton?.styleOverrides);
    expect(mui.components?.MuiPaper?.styleOverrides).toEqual(baseTheme.components?.MuiPaper?.styleOverrides);
    expect(themeVars(defaults)).toMatchObject({ '--site-radius-card': '56px', '--site-radius-button': '999px', '--site-heading-align': 'center' });
  });

  it('puts the heading font on headings and the text font on everything else', () => {
    const mui = brandTheme(styled({ typography: { headingFont: 'PLAYFAIR_DISPLAY', bodyFont: 'LATO', baseSize: 18 } }));

    expect(mui.typography.h1.fontFamily).toBe(FONTS.PLAYFAIR_DISPLAY.stack);
    expect(mui.typography.h6.fontFamily).toBe(FONTS.PLAYFAIR_DISPLAY.stack);
    // Every text variant, not just the top level: the base theme's variants carry their own font.
    for (const variant of ['body1', 'body2', 'button', 'caption', 'subtitle1'] as const) {
      expect(mui.typography[variant].fontFamily).toBe(FONTS.LATO.stack);
    }
    // 18px text: body text grows from 1rem to 1.125rem, and the rest in proportion.
    expect(mui.typography.body1.fontSize).toBe('1.125rem');
    expect(Number.parseFloat(String(mui.typography.body2.fontSize))).toBeCloseTo((Number.parseFloat(String(baseTheme.typography.body2.fontSize)) * 18) / 16, 3);
    expect(mui.typography.fontSize).toBeCloseTo(15.75);
  });

  it('shapes buttons and cards as chosen, in the booking flow too', () => {
    const mui = brandTheme(styled({ components: { buttonShape: 'SQUARE', buttonStyle: 'OUTLINE', cardStyle: 'SHADOW', shadow: 'STRONG', corners: 'SHARP' } }));
    const button = mui.components?.MuiButton?.styleOverrides as Record<string, Record<string, unknown>>;
    const paper = mui.components?.MuiPaper?.styleOverrides as Record<string, Record<string, unknown>>;

    expect(button.root.borderRadius).toBe(0);
    expect(button.containedPrimary.backgroundColor).toBe('transparent');
    expect(String(button.containedPrimary.boxShadow)).toContain(LIGHT_COLORS.primary);
    expect(paper.outlined.border).toBe(0);
    expect(String(paper.outlined.boxShadow)).toContain('0.18');
    expect(mui.shape.borderRadius).toBe(0);
    expect(themeVars(styled({ components: { corners: 'SHARP', buttonShape: 'SQUARE' } }))).toMatchObject({
      '--site-radius-card': '0px',
      '--site-radius-button': '0px',
    });
  });

  it('fills in typography and styling a response from before them lacks', () => {
    const theme = brandSiteTheme({ accentColor: '#0f9d76', darkTheme: false, theme: { colors: { ...LIGHT_COLORS, primary: '#0f9d76' } } as SiteTheme });

    expect(theme.typography).toEqual(DEFAULT_TYPOGRAPHY);
    expect(theme.components).toEqual(DEFAULT_COMPONENTS);
    expect(theme.colors.primary).toBe('#0f9d76');
  });

  it('measures contrast the WCAG way', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21);
    expect(contrastRatio('#777777', '#777777')).toBeCloseTo(1);
  });
});

describe('fonts', () => {
  it('offers every font the server accepts, each self-hosted except the device font', () => {
    expect(FONT_KEYS).toHaveLength(17);
    for (const font of FONT_KEYS) {
      expect(FONTS[font].stack).toBeTruthy();
      expect(Boolean(FONTS[font].load)).toBe(font !== 'SYSTEM');
    }
  });

  it('marks the capitals-only font as for headings only', () => {
    expect(FONT_KEYS.filter((font) => FONTS[font].headingsOnly)).toEqual(['BEBAS_NEUE']);
  });
});
