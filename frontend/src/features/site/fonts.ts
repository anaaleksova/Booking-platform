import { useEffect } from 'react';
import type { SiteFont, SiteTypography } from './types';

/** The device's own font: what every page used before fonts could be chosen. */
export const SYSTEM_STACK = '"Inter", "Segoe UI", system-ui, -apple-system, sans-serif';

export interface FontOption {
  label: string;
  kind: 'Sans-serif' | 'Serif' | 'Display';
  /** The CSS font-family, with a fallback of the same kind. */
  stack: string;
  /** Has Cyrillic letters (Macedonian, Serbian, Bulgarian …); without them those letters fall back to the device font. */
  cyrillic: boolean;
  /** Capitals only: for headings, never for running text. */
  headingsOnly?: boolean;
  /**
   * Loads the font's stylesheet. Self-hosted from the app's own origin (no
   * font service sees the visitors), and only when a page uses the font; the
   * browser then downloads only the alphabets on the page.
   */
  load?: () => Promise<unknown>;
}

const sans = (family: string) => `"${family}", ${SYSTEM_STACK}`;
const serif = (family: string) => `"${family}", Georgia, "Times New Roman", serif`;

export const FONTS: Record<SiteFont, FontOption> = {
  SYSTEM: { label: 'Device font', kind: 'Sans-serif', stack: SYSTEM_STACK, cyrillic: true },
  INTER: { label: 'Inter', kind: 'Sans-serif', stack: sans('Inter Variable'), cyrillic: true, load: () => import('@fontsource-variable/inter') },
  ROBOTO: { label: 'Roboto', kind: 'Sans-serif', stack: sans('Roboto Variable'), cyrillic: true, load: () => import('@fontsource-variable/roboto') },
  OPEN_SANS: { label: 'Open Sans', kind: 'Sans-serif', stack: sans('Open Sans Variable'), cyrillic: true, load: () => import('@fontsource-variable/open-sans') },
  LATO: {
    label: 'Lato',
    kind: 'Sans-serif',
    stack: sans('Lato'),
    cyrillic: false,
    load: () => Promise.all([import('@fontsource/lato/400.css'), import('@fontsource/lato/700.css'), import('@fontsource/lato/900.css')]),
  },
  MONTSERRAT: { label: 'Montserrat', kind: 'Sans-serif', stack: sans('Montserrat Variable'), cyrillic: true, load: () => import('@fontsource-variable/montserrat') },
  POPPINS: {
    label: 'Poppins',
    kind: 'Sans-serif',
    stack: sans('Poppins'),
    cyrillic: false,
    load: () =>
      Promise.all([
        import('@fontsource/poppins/400.css'),
        import('@fontsource/poppins/600.css'),
        import('@fontsource/poppins/700.css'),
        import('@fontsource/poppins/800.css'),
      ]),
  },
  NUNITO: { label: 'Nunito', kind: 'Sans-serif', stack: sans('Nunito Variable'), cyrillic: true, load: () => import('@fontsource-variable/nunito') },
  WORK_SANS: { label: 'Work Sans', kind: 'Sans-serif', stack: sans('Work Sans Variable'), cyrillic: false, load: () => import('@fontsource-variable/work-sans') },
  RALEWAY: { label: 'Raleway', kind: 'Sans-serif', stack: sans('Raleway Variable'), cyrillic: true, load: () => import('@fontsource-variable/raleway') },
  PLAYFAIR_DISPLAY: { label: 'Playfair Display', kind: 'Serif', stack: serif('Playfair Display Variable'), cyrillic: true, load: () => import('@fontsource-variable/playfair-display') },
  LORA: { label: 'Lora', kind: 'Serif', stack: serif('Lora Variable'), cyrillic: true, load: () => import('@fontsource-variable/lora') },
  MERRIWEATHER: { label: 'Merriweather', kind: 'Serif', stack: serif('Merriweather Variable'), cyrillic: true, load: () => import('@fontsource-variable/merriweather') },
  CORMORANT_GARAMOND: {
    label: 'Cormorant Garamond',
    kind: 'Serif',
    stack: serif('Cormorant Garamond'),
    cyrillic: true,
    load: () =>
      Promise.all([
        import('@fontsource/cormorant-garamond/400.css'),
        import('@fontsource/cormorant-garamond/600.css'),
        import('@fontsource/cormorant-garamond/700.css'),
      ]),
  },
  DM_SERIF_DISPLAY: { label: 'DM Serif Display', kind: 'Display', stack: serif('DM Serif Display'), cyrillic: false, load: () => import('@fontsource/dm-serif-display/400.css') },
  OSWALD: { label: 'Oswald', kind: 'Display', stack: sans('Oswald Variable'), cyrillic: true, load: () => import('@fontsource-variable/oswald') },
  BEBAS_NEUE: { label: 'Bebas Neue', kind: 'Display', stack: sans('Bebas Neue'), cyrillic: false, headingsOnly: true, load: () => import('@fontsource/bebas-neue/400.css') },
};

export const FONT_KEYS = Object.keys(FONTS) as SiteFont[];

const loaded = new Set<SiteFont>();

/** Loads these fonts once each; the device font needs nothing. */
export function loadFonts(fonts: SiteFont[]): Promise<unknown> {
  return Promise.all(
    fonts.map((font) => {
      const option = FONTS[font];
      if (!option?.load || loaded.has(font)) {
        return null;
      }
      loaded.add(font);
      return option.load().catch(() => loaded.delete(font));
    }),
  );
}

/** Loads the heading and text fonts of a theme. */
export function useThemeFonts(typography: SiteTypography) {
  useEffect(() => {
    void loadFonts([typography.headingFont, typography.bodyFont]);
  }, [typography.headingFont, typography.bodyFont]);
}
