import { useState } from 'react';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Rating from '@mui/material/Rating';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import FormatQuoteRoundedIcon from '@mui/icons-material/FormatQuoteRounded';
import { mediaUrl } from '@/features/media/api';
import { Reveal } from '@/features/public/Reveal';
import { siteImageUrl } from '../api';
import type { Testimonial, TestimonialsSection } from '../types';
import { SectionBody, SectionHeading, textOr, type SectionProps } from './shared';

function useTestimonials(section: TestimonialsSection) {
  return {
    items: section.content.items ?? [],
    eyebrow: textOr(section.content.eyebrow, 'Reviews'),
    title: textOr(section.content.title, 'What our guests say'),
  };
}

function Author({ item }: { item: Testimonial }) {
  return (
    <Stack direction="row" spacing={1.5} alignItems="center">
      <Avatar src={item.photo ? mediaUrl(siteImageUrl(item.photo.mediaId)) : undefined} alt={item.photo?.alt ?? ''} sx={{ bgcolor: 'var(--site-accent)' }}>
        {item.author.charAt(0)}
      </Avatar>
      <Box>
        <Typography fontWeight={700} component="cite" sx={{ fontStyle: 'normal', display: 'block' }}>
          {item.author}
        </Typography>
        {item.detail ? (
          <Typography variant="body2" color="text.secondary">
            {item.detail}
          </Typography>
        ) : null}
      </Box>
    </Stack>
  );
}

function Stars({ rating }: { rating?: number | null }) {
  return rating ? <Rating value={rating} readOnly size="small" sx={{ color: 'var(--site-accent)' }} /> : null;
}

/** A card per review, three across. */
export function TestimonialsCards({ section }: SectionProps<TestimonialsSection>) {
  const { items, eyebrow, title } = useTestimonials(section);
  if (items.length === 0) {
    return null;
  }
  return (
    <SectionBody>
      <SectionHeading section={section} eyebrow={eyebrow} title={title} />
      <Grid container spacing={3}>
        {items.map((item, index) => (
          <Grid item xs={12} md={4} key={index}>
            <Reveal delay={(index % 3) * 100} sx={{ height: '100%' }}>
              <Paper component="figure" variant="outlined" sx={{ m: 0, p: 3, height: '100%', borderRadius: 'var(--site-radius-card, 56px)', display: 'flex', flexDirection: 'column', gap: 2 }}>
                <Stars rating={item.rating} />
                <Typography component="blockquote" sx={{ m: 0, flex: 1, fontSize: 17, lineHeight: 1.6 }}>
                  “{item.quote}”
                </Typography>
                <Box component="figcaption">
                  <Author item={item} />
                </Box>
              </Paper>
            </Reveal>
          </Grid>
        ))}
      </Grid>
    </SectionBody>
  );
}

/** One review at a time, large, with buttons to the previous and next. */
export function TestimonialsSlider({ section }: SectionProps<TestimonialsSection>) {
  const { items, eyebrow, title } = useTestimonials(section);
  const [index, setIndex] = useState(0);
  if (items.length === 0) {
    return null;
  }
  const item = items[Math.min(index, items.length - 1)];
  const go = (step: number) => setIndex((current) => (current + step + items.length) % items.length);
  return (
    <SectionBody maxWidth="md" sx={{ textAlign: 'center' }}>
      <SectionHeading section={section} eyebrow={eyebrow} title={title} align="center" />
      <Box component="figure" sx={{ m: 0 }} aria-live="polite">
        <FormatQuoteRoundedIcon sx={{ fontSize: 56, color: 'var(--site-accent)' }} />
        <Typography component="blockquote" sx={{ m: 0, fontSize: { xs: 20, md: 26 }, lineHeight: 1.5, fontWeight: 500 }}>
          {item.quote}
        </Typography>
        <Stack component="figcaption" alignItems="center" spacing={1} sx={{ mt: 3 }}>
          <Stars rating={item.rating} />
          <Author item={item} />
        </Stack>
      </Box>
      {items.length > 1 ? (
        <Stack direction="row" justifyContent="center" alignItems="center" spacing={2} sx={{ mt: 3 }}>
          <IconButton onClick={() => go(-1)} aria-label="Previous review" sx={{ border: 1, borderColor: 'divider' }}>
            <ChevronLeftRoundedIcon />
          </IconButton>
          <Typography variant="body2" color="text.secondary">
            {index + 1} / {items.length}
          </Typography>
          <IconButton onClick={() => go(1)} aria-label="Next review" sx={{ border: 1, borderColor: 'divider' }}>
            <ChevronRightRoundedIcon />
          </IconButton>
        </Stack>
      ) : null}
    </SectionBody>
  );
}

/** The first review alone, big, on the primary colour. */
export function TestimonialsFeatured({ section }: SectionProps<TestimonialsSection>) {
  const { items } = useTestimonials(section);
  const item = items[0];
  if (!item) {
    return null;
  }
  return (
    <SectionBody maxWidth="md">
      <Reveal>
        <Box component="figure" sx={{ m: 0, p: { xs: 4, md: 7 }, borderRadius: 'calc(var(--site-radius-card, 56px) * 1.25)', bgcolor: 'var(--site-primary)', color: 'var(--site-on-primary)', textAlign: 'center' }}>
          {section.content.title ? (
            <Typography id={`section-${section.id}-title`} variant="overline" sx={{ letterSpacing: '0.3em', fontWeight: 700, opacity: 0.85 }}>
              {section.content.title}
            </Typography>
          ) : null}
          <Typography component="blockquote" sx={{ m: 0, mt: 1, fontSize: { xs: 22, md: 32 }, lineHeight: 1.4, fontWeight: 700 }}>
            “{item.quote}”
          </Typography>
          <Stack component="figcaption" alignItems="center" spacing={0.5} sx={{ mt: 3 }}>
            {item.rating ? <Rating value={item.rating} readOnly sx={{ color: 'inherit' }} /> : null}
            <Typography fontWeight={700} component="cite" sx={{ fontStyle: 'normal' }}>
              {item.author}
            </Typography>
            {item.detail ? <Typography sx={{ opacity: 0.8 }}>{item.detail}</Typography> : null}
          </Stack>
        </Box>
      </Reveal>
    </SectionBody>
  );
}
