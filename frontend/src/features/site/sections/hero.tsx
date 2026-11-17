import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { keyframes } from '@mui/material/styles';
import CalendarMonthRoundedIcon from '@mui/icons-material/CalendarMonthRounded';
import { mediaUrl } from '@/features/media/api';
import { siteImageUrl } from '../api';
import { useSiteData } from '../render/SiteData';
import { useSiteTheme } from '../render/SiteThemeScope';
import type { HeroSection, SiteButton } from '../types';
import { headingId, SiteButtons, SiteImg, textOr, useImageShape, useOnlyFree, type SectionProps } from './shared';

const riseIn = keyframes`
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: none; }
`;

const slowZoom = keyframes`
  from { transform: scale(1.12); }
  to { transform: scale(1); }
`;

const gradientDrift = keyframes`
  0%, 100% { background-position: 0% 0%; }
  50% { background-position: 100% 60%; }
`;

const softPulse = keyframes`
  0%, 100% { box-shadow: 0 10px 30px rgba(0,0,0,0.25), 0 0 0 0 rgba(255,255,255,0.35); }
  50% { box-shadow: 0 10px 30px rgba(0,0,0,0.25), 0 0 0 10px rgba(255,255,255,0); }
`;

/** Staggered entrance for the hero's lines; `step` is the order. */
const heroLine = (step: number) => ({
  opacity: 0,
  animation: `${riseIn} 800ms cubic-bezier(0.22, 1, 0.36, 1) ${150 + step * 140}ms forwards`,
  '@media (prefers-reduced-motion: reduce)': { opacity: 1, animation: 'none' },
});

const SHADE = 'linear-gradient(180deg, rgba(10,12,20,0.55) 0%, rgba(10,12,20,0.45) 50%, rgba(10,12,20,0.75) 100%)';

/** The hero's texts and buttons with the business's own as defaults. */
function useHeroContent(section: HeroSection) {
  const { business } = useSiteData();
  const free = useOnlyFree();
  const content = section.content;
  const buttons: SiteButton[] = content.buttons ?? [
    { label: free ? 'See free times' : 'See free times & prices', action: 'BOOKING' },
  ];
  const images = content.images ?? [];
  return {
    eyebrow: textOr(content.eyebrow, 'Welcome'),
    title: textOr(content.title, business.name),
    subtitle: textOr(content.subtitle, business.tagline ?? ''),
    buttons,
    images,
    /** The first image, or the business's cover photo when the hero has none. */
    cover: images.length ? mediaUrl(siteImageUrl(images[0].mediaId)) : mediaUrl(business.coverUrl),
    coverAlt: images[0]?.alt ?? '',
  };
}

/** The lines of a hero over a dark image: eyebrow, name, subtitle, buttons, each rising in after the last. */
function HeroLines({ section, align = 'center' }: { section: HeroSection; align?: 'center' | 'left' }) {
  const { eyebrow, title, subtitle, buttons } = useHeroContent(section);
  const { buttonStyle } = useSiteTheme().components;
  return (
    <Stack alignItems={align === 'center' ? 'center' : 'flex-start'} spacing={2} sx={{ maxWidth: 760, position: 'relative', textAlign: align }}>
      {eyebrow ? (
        <Typography variant="overline" sx={{ letterSpacing: '0.35em', color: 'rgba(255,255,255,0.85)', ...heroLine(0) }}>
          {eyebrow}
        </Typography>
      ) : null}
      <Typography id={headingId(section)} component="h1" sx={{ fontSize: { xs: 44, sm: 64, md: 84 }, fontWeight: 800, lineHeight: 1, letterSpacing: '-0.02em', ...heroLine(1) }}>
        {title}
      </Typography>
      {subtitle ? (
        <Typography sx={{ fontSize: { xs: 17, md: 20 }, maxWidth: 620, ...heroLine(2) }}>
          <Box component="span" sx={{ opacity: 0.92 }}>{subtitle}</Box>
        </Typography>
      ) : null}
      <Box sx={{ mt: 2, ...heroLine(3) }}>
        <SiteButtons
          buttons={buttons}
          justify={align === 'center' ? 'center' : 'flex-start'}
          firstProps={{
            startIcon: buttons[0]?.action === 'BOOKING' ? <CalendarMonthRoundedIcon /> : undefined,
            sx: {
              py: 1.5,
              fontSize: 17,
              transition: 'transform 200ms ease',
              '&:hover': { transform: 'translateY(-2px)' },
              ...(buttonStyle === 'FILLED'
                ? {
                    boxShadow: '0 10px 30px rgba(0,0,0,0.25)',
                    animation: `${softPulse} 2.8s ease-in-out 1.6s infinite`,
                    '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
                  }
                : // Over the dark photo an outline or text button is drawn in white, or it would not be seen.
                  {
                    color: '#fff',
                    boxShadow: buttonStyle === 'OUTLINE' ? 'inset 0 0 0 2px #fff' : 'none',
                    '&:hover': { transform: 'translateY(-2px)', bgcolor: 'rgba(255,255,255,0.12)' },
                  }),
            },
          }}
          restProps={{ sx: { py: 1.5, fontSize: 17, color: '#fff', borderColor: 'rgba(255,255,255,0.6)', '&:hover': { borderColor: '#fff' } } }}
        />
      </Box>
    </Stack>
  );
}

/** The frame of the image-backed heroes: full height, white text, the content centred. */
function DarkHero({ children, background }: { children: React.ReactNode; background: React.ReactNode }) {
  return (
    <Box
      sx={{
        position: 'relative',
        overflow: 'hidden',
        minHeight: { xs: 560, md: 640 },
        display: 'grid',
        placeItems: 'center',
        textAlign: 'center',
        color: '#fff',
        px: 2,
        pt: 10,
        pb: 6,
        backgroundColor: 'var(--site-primary)',
      }}
    >
      {background}
      {children}
    </Box>
  );
}

/** The photo (slowly settling), or the brand colour drifting when there is none. */
function CoverBackground({ url }: { url?: string }) {
  return (
    <Box
      aria-hidden
      sx={{
        position: 'absolute',
        inset: 0,
        backgroundImage: url
          ? `${SHADE}, url("${url}")`
          : (theme) => `radial-gradient(circle at 30% 20%, ${theme.palette.primary.light} 0%, ${theme.palette.primary.dark} 55%, #111214 100%)`,
        backgroundSize: url ? 'cover' : '160% 160%',
        backgroundPosition: 'center',
        animation: url ? `${slowZoom} 14s ease-out forwards` : `${gradientDrift} 18s ease-in-out infinite`,
        '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
      }}
    />
  );
}

/** The hero every page has always had: the name over the cover photo. */
export function HeroFullWidth({ section }: SectionProps<HeroSection>) {
  const { cover } = useHeroContent(section);
  return (
    <DarkHero background={<CoverBackground url={cover} />}>
      <HeroLines section={section} />
    </DarkHero>
  );
}

/** Several photos crossfading, each slowly zooming: motion without a video. */
export function HeroSlideshow({ section }: SectionProps<HeroSection>) {
  const { images, cover } = useHeroContent(section);
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const reduced = typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (images.length < 2 || reduced) {
      return undefined;
    }
    const timer = window.setInterval(() => setIndex((current) => (current + 1) % images.length), 6000);
    return () => window.clearInterval(timer);
  }, [images.length]);

  const slides = images.length
    ? images.map((image) => mediaUrl(siteImageUrl(image.mediaId))!)
    : cover ? [cover] : [];

  return (
    <DarkHero
      background={
        slides.length ? (
          slides.map((url, slide) => (
            <Box
              key={url}
              aria-hidden
              sx={{
                position: 'absolute',
                inset: 0,
                backgroundImage: `${SHADE}, url("${url}")`,
                backgroundSize: 'cover',
                backgroundPosition: 'center',
                opacity: slide === index ? 1 : 0,
                transform: slide === index ? 'scale(1)' : 'scale(1.08)',
                transition: 'opacity 1.4s ease, transform 7s ease-out',
                '@media (prefers-reduced-motion: reduce)': { transition: 'none', transform: 'none' },
              }}
            />
          ))
        ) : (
          <CoverBackground />
        )
      }
    >
      <HeroLines section={section} />
    </DarkHero>
  );
}

/**
 * The embed address for a YouTube or Vimeo link, playing muted and looped
 * with no controls: a moving background, not a player. Only these two
 * providers are accepted by the server, and the address is built here, never
 * taken from the link.
 */
export function backgroundVideoSrc(link?: string | null): string | null {
  if (!link) {
    return null;
  }
  const youtube = /(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/shorts\/)([\w-]{6,20})/.exec(link);
  if (youtube) {
    const id = youtube[1];
    return `https://www.youtube-nocookie.com/embed/${id}?autoplay=1&mute=1&loop=1&playlist=${id}&controls=0&playsinline=1&modestbranding=1&rel=0`;
  }
  const vimeo = /vimeo\.com\/(\d{4,12})/.exec(link);
  return vimeo ? `https://player.vimeo.com/video/${vimeo[1]}?background=1&autoplay=1&muted=1&loop=1` : null;
}

/** A muted, looping video behind the name; the cover photo shows until it plays, and instead of it for reduced motion. */
export function HeroVideo({ section }: SectionProps<HeroSection>) {
  const { cover } = useHeroContent(section);
  const src = backgroundVideoSrc(section.content.videoUrl);
  const reduced = typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  return (
    <DarkHero
      background={
        <>
          <CoverBackground url={cover} />
          {src && !reduced ? (
            <Box aria-hidden sx={{ position: 'absolute', inset: 0, overflow: 'hidden', pointerEvents: 'none' }}>
              <Box
                component="iframe"
                src={src}
                title=""
                tabIndex={-1}
                allow="autoplay; encrypted-media"
                loading="lazy"
                sx={{
                  border: 0,
                  position: 'absolute',
                  top: '50%',
                  left: '50%',
                  // 16:9 video covering the whole box, whatever its shape.
                  width: 'max(100%, 177.78vh)',
                  height: 'max(100%, 56.25vw)',
                  minHeight: '100%',
                  transform: 'translate(-50%, -50%)',
                }}
              />
              <Box sx={{ position: 'absolute', inset: 0, background: SHADE }} />
            </Box>
          ) : null}
        </>
      }
    >
      <HeroLines section={section} />
    </DarkHero>
  );
}

/** Text on the left, the photo on the right, on the page colour. */
export function HeroSplit({ section }: SectionProps<HeroSection>) {
  const shape = useImageShape();
  const { eyebrow, title, subtitle, buttons, cover, coverAlt, images } = useHeroContent(section);
  return (
    <Box sx={{ py: { xs: 5, md: 7 } }}>
      <Container maxWidth="lg">
        <Grid container spacing={{ xs: 4, md: 6 }} alignItems="center">
          <Grid item xs={12} md={6}>
            <Stack spacing={2.5}>
              {eyebrow ? (
                <Typography variant="overline" sx={{ letterSpacing: '0.3em', fontWeight: 700, color: 'var(--site-accent)', ...heroLine(0) }}>
                  {eyebrow}
                </Typography>
              ) : null}
              <Typography id={headingId(section)} component="h1" sx={{ fontSize: { xs: 40, md: 64 }, fontWeight: 800, lineHeight: 1.05, letterSpacing: '-0.02em', ...heroLine(1) }}>
                {title}
              </Typography>
              {subtitle ? (
                <Typography color="text.secondary" sx={{ fontSize: { xs: 17, md: 20 }, ...heroLine(2) }}>
                  {subtitle}
                </Typography>
              ) : null}
              <Box sx={{ pt: 1, ...heroLine(3) }}>
                <SiteButtons buttons={buttons} justify="flex-start" />
              </Box>
            </Stack>
          </Grid>
          <Grid item xs={12} md={6}>
            <Box sx={{ aspectRatio: '4 / 3', ...shape(56), overflow: 'hidden', bgcolor: 'var(--site-primary)', ...heroLine(2) }}>
              {images[0] ? (
                <SiteImg image={images[0]} eager />
              ) : cover ? (
                <Box component="img" src={cover} alt={coverAlt} sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
              ) : null}
            </Box>
          </Grid>
        </Grid>
      </Container>
    </Box>
  );
}

/** Just words: the name large and centred on the page colour, no image. */
export function HeroCentered({ section }: SectionProps<HeroSection>) {
  const { eyebrow, title, subtitle, buttons } = useHeroContent(section);
  return (
    <Box sx={{ py: { xs: 6, md: 10 }, textAlign: 'center' }}>
      <Container maxWidth="md">
        <Stack alignItems="center" spacing={2.5}>
          {eyebrow ? (
            <Typography variant="overline" sx={{ letterSpacing: '0.35em', fontWeight: 700, color: 'var(--site-accent)', ...heroLine(0) }}>
              {eyebrow}
            </Typography>
          ) : null}
          <Typography id={headingId(section)} component="h1" sx={{ fontSize: { xs: 44, sm: 64, md: 88 }, fontWeight: 800, lineHeight: 1, letterSpacing: '-0.03em', ...heroLine(1) }}>
            {title}
          </Typography>
          {subtitle ? (
            <Typography color="text.secondary" sx={{ fontSize: { xs: 17, md: 21 }, maxWidth: 620, ...heroLine(2) }}>
              {subtitle}
            </Typography>
          ) : null}
          <Box sx={{ pt: 1, ...heroLine(3) }}>
            <SiteButtons buttons={buttons} />
          </Box>
        </Stack>
      </Container>
    </Box>
  );
}
