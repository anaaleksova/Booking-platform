import { createContext, useContext, type ReactNode } from 'react';
import Box, { type BoxProps } from '@mui/material/Box';
import { ThemeProvider } from '@mui/material/styles';
import { brandTheme } from '@/features/public/brandTheme';
import { FONTS, useThemeFonts } from '../fonts';
import { DEFAULT_TYPOGRAPHY, LIGHT_COLORS, completeTheme, themeVars } from '../theme';
import type { SiteTheme } from '../types';

const SiteThemeContext = createContext<SiteTheme>(completeTheme({ colors: LIGHT_COLORS }));

/** The site theme in effect: for the few choices that cannot be CSS alone (a circle photo's crop). */
export function useSiteTheme(): SiteTheme {
  return useContext(SiteThemeContext);
}

/**
 * Draws its children in a site theme: the MUI theme for components, the
 * `--site-*` custom properties for everything else, the chosen fonts loaded,
 * and the heading style applied to every heading inside.
 */
export function SiteThemeScope({ theme, children, sx }: { theme: SiteTheme; children: ReactNode; sx?: BoxProps['sx'] }) {
  useThemeFonts(theme.typography);
  return (
    <SiteThemeContext.Provider value={theme}>
      <ThemeProvider theme={brandTheme(theme)}>
        <Box
          style={themeVars(theme) as React.CSSProperties}
          // The text font on the root too, for text that is not in a Typography of its own.
          sx={[{ bgcolor: 'background.default', color: 'text.primary', fontFamily: (mui) => mui.typography.fontFamily }, headingStyles(theme), ...(Array.isArray(sx) ? sx : [sx])]}
        >
          {children}
        </Box>
      </ThemeProvider>
    </SiteThemeContext.Provider>
  );
}

/**
 * The chosen heading font, weight and style on every heading, whatever its
 * component set inline. Only what differs from the defaults is set, so an
 * unstyled page is exactly as before.
 */
function headingStyles({ typography }: SiteTheme): BoxProps['sx'] {
  const rule: Record<string, string | number> = {};
  if (typography.headingFont !== DEFAULT_TYPOGRAPHY.headingFont) {
    rule.fontFamily = FONTS[typography.headingFont].stack;
  }
  if (typography.headingWeight !== DEFAULT_TYPOGRAPHY.headingWeight) {
    rule.fontWeight = typography.headingWeight;
  }
  if (typography.headingStyle === 'UPPERCASE') {
    rule.textTransform = 'uppercase';
    rule.letterSpacing = '0.04em';
  } else if (typography.headingStyle === 'ITALIC') {
    rule.fontStyle = 'italic';
  }
  return Object.keys(rule).length ? { '& :is(h1, h2, h3, h4)': rule } : {};
}
