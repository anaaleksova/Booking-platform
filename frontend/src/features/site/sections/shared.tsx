import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Box, { type BoxProps } from '@mui/material/Box';
import Button, { type ButtonProps } from '@mui/material/Button';
import Container, { type ContainerProps } from '@mui/material/Container';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import { mediaUrl } from '@/features/media/api';
import { bookLabel, onlyFree } from '@/features/public/freeBooking';
import { Reveal } from '@/features/public/Reveal';
import type { DayInterval, PublicService, SpecialHours } from '@/types/api';
import { DAY_NAMES, describeSpecialHours, formatInterval, formatShortDate } from '@/utils/format';
import { siteImageUrl } from '../api';
import { useSiteData } from '../render/SiteData';
import { useSiteTheme } from '../render/SiteThemeScope';
import type { Section, ServiceSource, SiteButton, SiteImage } from '../types';

/** What every layout component receives: its own section, already of the right type. */
export interface SectionProps<S extends Section> {
  section: S;
}

/**
 * A text the owner may leave empty: null shows the default, an empty string
 * shows nothing, anything else shows itself.
 */
export function textOr(value: string | null | undefined, fallback: string): string {
  return value ?? fallback;
}

/** The id of the section's heading, which names the section for screen readers. */
export function headingId(section: Section): string {
  return `section-${section.id}-title`;
}

/** The usual vertical rhythm and width of a section's content. */
export function SectionBody({ children, maxWidth = 'lg', sx }: { children: ReactNode; maxWidth?: ContainerProps['maxWidth']; sx?: BoxProps['sx'] }) {
  return (
    <Box sx={[{ py: { xs: 4, md: 6 } }, ...(Array.isArray(sx) ? sx : [sx])]}>
      <Container maxWidth={maxWidth}>{children}</Container>
    </Box>
  );
}

/**
 * Eyebrow and heading, as every section of the page has always had them.
 * Empty texts are left out. Aligned as the theme says, unless the layout
 * puts the heading beside something and needs it on the left.
 */
export function SectionHeading({
  section,
  eyebrow,
  title,
  align,
}: {
  section: Section;
  eyebrow: string;
  title: string;
  align?: 'center' | 'left';
}) {
  if (!eyebrow && !title) {
    return null;
  }
  return (
    <Reveal>
      <Stack
        spacing={1}
        sx={{
          mb: { xs: 3, md: 4 },
          alignItems: align ? (align === 'center' ? 'center' : 'flex-start') : 'var(--site-heading-items, center)',
          textAlign: align ?? 'var(--site-heading-align, center)',
        }}
      >
        {eyebrow ? (
          <Typography variant="overline" sx={{ letterSpacing: '0.3em', fontWeight: 700, color: 'var(--site-accent)' }}>
            {eyebrow}
          </Typography>
        ) : null}
        {title ? (
          <Typography id={headingId(section)} component="h2" sx={{ fontSize: { xs: 30, md: 42 }, fontWeight: 800, letterSpacing: '-0.02em' }}>
            {title}
          </Typography>
        ) : null}
      </Stack>
    </Reveal>
  );
}

/** Where a button leads: the booking flow, a section of this page, or a link. */
export function buttonLinkProps(button: SiteButton, slug: string): Partial<ButtonProps> & Record<string, unknown> {
  switch (button.action) {
    case 'BOOKING':
      return { component: RouterLink, to: `/b/${slug}/book` };
    case 'SECTION':
      return {
        href: `#section-${button.target}`,
        onClick: (event: React.MouseEvent) => {
          const target = document.getElementById(`section-${button.target}`);
          if (target) {
            event.preventDefault();
            target.scrollIntoView({ behavior: 'smooth', block: 'start' });
          }
        },
      };
    case 'URL': {
      const external = /^https?:/i.test(button.target ?? '');
      return { href: button.target ?? undefined, ...(external ? { target: '_blank', rel: 'noopener noreferrer' } : {}) };
    }
    default:
      return {};
  }
}

/** A row of buttons; the first one is the main one. */
export function SiteButtons({
  buttons,
  size = 'large',
  justify = 'center',
  firstProps,
  restProps,
}: {
  buttons: SiteButton[];
  size?: ButtonProps['size'];
  justify?: 'center' | 'flex-start';
  firstProps?: Partial<ButtonProps>;
  restProps?: Partial<ButtonProps>;
}) {
  const { business } = useSiteData();
  if (buttons.length === 0) {
    return null;
  }
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} justifyContent={justify} alignItems="center">
      {buttons.map((button, index) => (
        <Button
          key={`${button.label}-${index}`}
          size={size}
          variant={index === 0 ? 'contained' : 'outlined'}
          {...(index === 0 ? firstProps : restProps)}
          {...buttonLinkProps(button, business.slug)}
          sx={[{ borderRadius: 'var(--site-radius-button, 999px)', px: 4 }, ...[(index === 0 ? firstProps : restProps)?.sx ?? {}].flat()]}
        >
          {button.label}
        </Button>
      ))}
    </Stack>
  );
}

/** The booking button the page shows when the owner did not choose buttons. */
export function useDefaultBookButton(label?: string): SiteButton {
  const { services } = useSiteData();
  return { label: label ?? bookLabel(services), action: 'BOOKING' };
}

/** Whether the business takes only free reservations (a restaurant's tables): then prices are not shown. */
export function useOnlyFree(): boolean {
  return onlyFree(useSiteData().services);
}

/**
 * An uploaded image, loaded lazily. One that has since been deleted hides
 * itself instead of showing a broken picture.
 */
export function SiteImg({ image, sx, eager = false }: { image: SiteImage; sx?: BoxProps['sx']; eager?: boolean }) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return null;
  }
  return (
    <Box
      component="img"
      src={mediaUrl(siteImageUrl(image.mediaId))}
      alt={image.alt ?? ''}
      loading={eager ? 'eager' : 'lazy'}
      decoding="async"
      onError={() => setFailed(true)}
      sx={[{ display: 'block', width: '100%', height: '100%', objectFit: 'cover' }, ...(Array.isArray(sx) ? sx : [sx])]}
    />
  );
}

/**
 * The corners of a photo in the theme's photo shape: square, the photo's
 * usual rounding, or a circle (cropped square). Photos that must keep their
 * proportions (a gallery) pass `circle: false` and stay rounded instead.
 */
export function useImageShape(): (roundedPx: number, options?: { circle?: boolean }) => { borderRadius: string; aspectRatio?: string } {
  const { imageShape } = useSiteTheme().components;
  return (roundedPx, { circle = true } = {}) => {
    if (imageShape === 'SQUARE') {
      return { borderRadius: '0px' };
    }
    if (imageShape === 'CIRCLE' && circle) {
      return { borderRadius: '50%', aspectRatio: '1 / 1' };
    }
    return { borderRadius: `${roundedPx}px` };
  };
}

/** The services a section shows: all bookable ones, or the picked ones in the picked order. */
export function useSectionServices(source: ServiceSource, ids?: number[] | null): PublicService[] {
  const { services } = useSiteData();
  if (source === 'ALL') {
    return services;
  }
  const byId = new Map(services.map((service) => [service.id, service]));
  return (ids ?? []).map((id) => byId.get(id)).filter((service): service is PublicService => Boolean(service));
}

/**
 * A row that scrolls sideways and snaps item by item, with arrows when there
 * is more to see. The arrows are real buttons, so it works by keyboard too.
 *
 * @param itemWidth CSS width of one item per breakpoint, e.g. `{ xs: '78%', md: 'calc((100% - 24px) / 3)' }`
 */
export function Carousel({
  label,
  itemWidth,
  children,
}: {
  label: string;
  itemWidth: { xs: string; md: string };
  children: ReactNode[];
}) {
  const track = useRef<HTMLDivElement>(null);
  const [edges, setEdges] = useState({ start: true, end: false });

  const update = useCallback(() => {
    const node = track.current;
    if (node) {
      setEdges({ start: node.scrollLeft < 4, end: node.scrollLeft + node.clientWidth >= node.scrollWidth - 4 });
    }
  }, []);

  useEffect(() => {
    update();
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, [update, children.length]);

  const scrollBy = (direction: number) => {
    const node = track.current;
    const first = node?.firstElementChild;
    if (node && first) {
      // One item plus the 12px gap between items.
      node.scrollBy({ left: direction * (first.getBoundingClientRect().width + 12), behavior: 'smooth' });
    }
  };

  return (
    <Box sx={{ position: 'relative' }} role="region" aria-roledescription="carousel" aria-label={label}>
      <Box
        ref={track}
        onScroll={update}
        sx={{
          display: 'flex',
          gap: 1.5,
          overflowX: 'auto',
          scrollSnapType: 'x mandatory',
          scrollbarWidth: 'none',
          '&::-webkit-scrollbar': { display: 'none' },
          mx: { xs: -2, md: 0 },
          px: { xs: 2, md: 0 },
          pb: 1,
        }}
      >
        {children.map((child, index) => (
          <Box key={index} sx={{ flex: { xs: `0 0 ${itemWidth.xs}`, md: `0 0 ${itemWidth.md}` }, scrollSnapAlign: 'start' }}>
            {child}
          </Box>
        ))}
      </Box>
      {[
        { direction: -1, hidden: edges.start, name: 'Previous', side: { left: -20 }, icon: <ChevronLeftRoundedIcon /> },
        { direction: 1, hidden: edges.end, name: 'Next', side: { right: -20 }, icon: <ChevronRightRoundedIcon /> },
      ].map(({ direction, hidden, name, side, icon }) => (
        <IconButton
          key={direction}
          onClick={() => scrollBy(direction)}
          aria-label={name}
          tabIndex={hidden ? -1 : 0}
          sx={{
            display: { xs: 'none', md: 'inline-flex' },
            position: 'absolute',
            top: 'calc(50% - 28px)',
            ...side,
            bgcolor: '#fff',
            color: '#111214',
            boxShadow: '0 4px 16px rgba(0,0,0,0.18)',
            opacity: hidden ? 0 : 1,
            pointerEvents: hidden ? 'none' : 'auto',
            transition: 'opacity 200ms ease',
            '&:hover': { bgcolor: '#fff' },
          }}
        >
          {icon}
        </IconButton>
      ))}
    </Box>
  );
}

export function Meta({ icon, text }: { icon: ReactNode; text: string }) {
  return (
    <Stack direction="row" spacing={0.75} alignItems="center">
      {icon}
      <Typography variant="body2">{text}</Typography>
    </Stack>
  );
}

/** The week, today in bold. */
export function OpeningHours({
  hours,
  special = [],
  maxWidth = 360,
}: {
  hours: DayInterval[];
  /** Upcoming dates whose hours differ from the week's, listed under it. */
  special?: SpecialHours[];
  maxWidth?: number | string;
}) {
  const today = ((new Date().getDay() + 6) % 7) + 1;
  return (
    <Stack spacing={0.5}>
      {DAY_NAMES.map((name, index) => {
        const day = index + 1;
        const intervals = hours.filter((interval) => interval.dayOfWeek === day);
        return (
          <Stack key={name} direction="row" justifyContent="space-between" sx={{ fontWeight: day === today ? 700 : 400, maxWidth }}>
            <Typography variant="body2" fontWeight="inherit">
              {name}
            </Typography>
            <Typography variant="body2" fontWeight="inherit" color={intervals.length ? 'text.primary' : 'text.secondary'}>
              {intervals.length ? intervals.map(formatInterval).join(', ') : 'Closed'}
            </Typography>
          </Stack>
        );
      })}
      {special.length > 0 ? (
        <>
          <Typography variant="subtitle2" sx={{ pt: 1.5 }}>
            Special dates
          </Typography>
          {special.slice(0, 6).map((day) => (
            <Stack key={day.date} direction="row" justifyContent="space-between" spacing={2} sx={{ maxWidth }}>
              <Typography variant="body2">
                {formatShortDate(day.date)}
                {day.note ? ` · ${day.note}` : ''}
              </Typography>
              <Typography variant="body2" color={day.closed ? 'text.secondary' : 'text.primary'} sx={{ flexShrink: 0 }}>
                {describeSpecialHours(day)}
              </Typography>
            </Stack>
          ))}
        </>
      ) : null}
    </Stack>
  );
}
