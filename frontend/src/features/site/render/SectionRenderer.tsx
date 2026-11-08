import { Component, Suspense, useMemo, type ErrorInfo, type ReactNode } from 'react';
import Box from '@mui/material/Box';
import { ThemeProvider, createTheme, useTheme, type Theme } from '@mui/material/styles';
import { isHidden } from '../catalog';
import { readableOn, withAlpha } from '../theme';
import type { Section, SectionTone } from '../types';
import { layoutComponent } from './registry';

/**
 * One section of the page: looked up in the registry by type and layout,
 * framed in its tone and visibility, and isolated, so a section this app
 * cannot draw — unknown, or failing — leaves the rest of the page standing.
 */
export function SectionRenderer({ section }: { section: Section }) {
  const Layout = layoutComponent(section);
  if (!Layout) {
    if (import.meta.env.DEV) {
      console.warn(`Skipping section ${section.id}: no component for ${section.type} / ${section.layout}`);
    }
    return null;
  }
  if (isHidden(section)) {
    return null;
  }
  return (
    <SectionFrame section={section}>
      <SectionBoundary section={section}>
        <Suspense fallback={<Box sx={{ minHeight: 240 }} />}>
          <Layout section={section as never} />
        </Suspense>
      </SectionBoundary>
    </SectionFrame>
  );
}

const TONE_BACKGROUND: Record<SectionTone, string | null> = {
  DEFAULT: null,
  SURFACE: 'var(--site-surface)',
  PRIMARY: 'var(--site-primary)',
  SECONDARY: 'var(--site-secondary)',
};

/**
 * The `<section>` element: its anchor, its band colour and the screens it
 * shows on. On the primary or secondary colour, the section gets a theme of
 * its own whose texts read on that colour.
 */
function SectionFrame({ section, children }: { section: Section; children: ReactNode }) {
  const theme = useTheme();
  const tone = section.style?.tone ?? 'DEFAULT';
  const band = tone === 'PRIMARY' ? theme.palette.primary.main : tone === 'SECONDARY' ? theme.palette.secondary.main : null;
  const toneTheme = useMemo(() => (band ? onColour(theme, band) : null), [theme, band]);
  const { desktop, mobile } = section.visibility;

  const frame = (
    <Box
      component="section"
      id={`section-${section.id}`}
      aria-labelledby={`section-${section.id}-title`}
      data-section-type={section.type}
      sx={{
        display: { xs: mobile ? 'block' : 'none', md: desktop ? 'block' : 'none' },
        bgcolor: TONE_BACKGROUND[tone] ?? undefined,
        color: toneTheme ? toneTheme.palette.text.primary : undefined,
        scrollMarginTop: 72,
      }}
    >
      {children}
    </Box>
  );
  return toneTheme ? <ThemeProvider theme={toneTheme}>{frame}</ThemeProvider> : frame;
}

const toneCache = new WeakMap<Theme, Map<string, Theme>>();

/** The page theme for content on a coloured band: texts, cards and buttons that read on it. */
function onColour(theme: Theme, colour: string): Theme {
  const byColour = toneCache.get(theme) ?? new Map<string, Theme>();
  toneCache.set(theme, byColour);
  const cached = byColour.get(colour);
  if (cached) {
    return cached;
  }
  const on = readableOn(colour);
  const created = createTheme(theme, {
    palette: {
      mode: on === '#ffffff' ? 'dark' : 'light',
      primary: theme.palette.augmentColor({ color: { main: on } }),
      text: { primary: on, secondary: withAlpha(on, 0.75) },
      background: { default: colour, paper: withAlpha(on, 0.08) },
      divider: withAlpha(on, 0.2),
    },
  });
  byColour.set(colour, created);
  return created;
}

/** Keeps a failing section from taking the page down with it. */
class SectionBoundary extends Component<{ section: Section; children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(`Section ${this.props.section.id} (${this.props.section.type}) failed to render`, error, info.componentStack);
  }

  render() {
    return this.state.failed ? null : this.props.children;
  }
}
