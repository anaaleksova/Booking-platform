import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { mediaUrl } from '@/features/media/api';
import { Reveal } from '@/features/public/Reveal';
import { useSiteData } from '../render/SiteData';
import type { AboutSection } from '../types';
import { SectionBody, SectionHeading, SiteImg, textOr, useImageShape, type SectionProps } from './shared';

/** The texts, with the business's description as the default; null when there is nothing to say. */
function useAbout(section: AboutSection) {
  const { business } = useSiteData();
  return {
    eyebrow: textOr(section.content.eyebrow, 'About us'),
    title: textOr(section.content.title, `Welcome to ${business.name}`),
    text: textOr(section.content.text, business.description ?? ''),
    coverUrl: mediaUrl(business.coverUrl),
  };
}

function Body({ text, align = 'center' }: { text: string; align?: 'center' | 'left' }) {
  return (
    <Reveal delay={120}>
      <Typography color="text.secondary" sx={{ fontSize: 18, lineHeight: 1.7, whiteSpace: 'pre-line', textAlign: align }}>
        {text}
      </Typography>
    </Reveal>
  );
}

/** The welcome text centred, as the page has always had it. Nothing at all without a text. */
export function AboutCentered({ section }: SectionProps<AboutSection>) {
  const { eyebrow, title, text } = useAbout(section);
  if (!text) {
    return null;
  }
  return (
    <SectionBody maxWidth="md" sx={{ py: { xs: 4, md: 5 }, textAlign: 'center' }}>
      <SectionHeading section={section} eyebrow={eyebrow} title={title} align="center" />
      <Body text={text} />
    </SectionBody>
  );
}

/** The text beside a photo: the section's own, or the cover photo. */
export function AboutTextImage({ section }: SectionProps<AboutSection>) {
  const shape = useImageShape();
  const { eyebrow, title, text, coverUrl } = useAbout(section);
  const image = section.content.image;
  return (
    <SectionBody>
      <Grid container spacing={{ xs: 4, md: 8 }} alignItems="center">
        <Grid item xs={12} md={6}>
          <Reveal>
            <Box sx={{ aspectRatio: '4 / 5', maxHeight: 560, ...shape(56), overflow: 'hidden', bgcolor: 'var(--site-primary)' }}>
              {image ? (
                <SiteImg image={image} />
              ) : coverUrl ? (
                <Box component="img" src={coverUrl} alt="" loading="lazy" sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
              ) : null}
            </Box>
          </Reveal>
        </Grid>
        <Grid item xs={12} md={6}>
          <SectionHeading section={section} eyebrow={eyebrow} title={title} align="left" />
          {text ? <Body text={text} align="left" /> : null}
        </Grid>
      </Grid>
    </SectionBody>
  );
}

/** The text with a few numbers beside it: years open, guests served, … */
export function AboutStats({ section }: SectionProps<AboutSection>) {
  const { eyebrow, title, text } = useAbout(section);
  const stats = section.content.stats ?? [];
  return (
    <SectionBody>
      <Grid container spacing={{ xs: 4, md: 8 }} alignItems="center">
        <Grid item xs={12} md={stats.length ? 6 : 12}>
          <SectionHeading section={section} eyebrow={eyebrow} title={title} align="left" />
          {text ? <Body text={text} align="left" /> : null}
        </Grid>
        {stats.length ? (
          <Grid item xs={12} md={6}>
            <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 2 }}>
              {stats.map((stat, index) => (
                <Reveal key={`${stat.label}-${index}`} delay={index * 100}>
                  <Stack sx={{ p: 3, borderRadius: 'var(--site-radius-card, 56px)', bgcolor: 'background.paper', border: 1, borderColor: 'divider', height: '100%' }}>
                    <Typography component="dd" sx={{ m: 0, fontSize: { xs: 34, md: 44 }, fontWeight: 800, color: 'var(--site-primary)', letterSpacing: '-0.02em', order: 1 }}>
                      {stat.value}
                    </Typography>
                    <Typography component="dt" color="text.secondary" sx={{ order: 2 }}>
                      {stat.label}
                    </Typography>
                  </Stack>
                </Reveal>
              ))}
            </Box>
          </Grid>
        ) : null}
      </Grid>
    </SectionBody>
  );
}
