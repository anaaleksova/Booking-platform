import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded';
import { errorMessage } from '@/api/client';
import { ImageUpload } from '@/components/ImageUpload';
import { LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { toBusinessInput, useBusiness, useUpdateBusiness, type BusinessInput } from '@/features/business/api';
import { SiteDesigner } from '@/features/site/editor/SiteDesigner';
import { LivePreview } from '@/features/site/editor/LivePreview';
import type { SiteDocument } from '@/features/site/types';
import { GalleryManager } from './GalleryManager';

/**
 * How the business looks to its customers. The business details on top —
 * images, words, links — are saved straight away: emails and the chat
 * assistant use them too. The design below — theme and sections — is a draft
 * until it is published. Only this business's page is ever reachable from
 * its link.
 */
export function BookingPageTab() {
  const toast = useToast();
  const { data: business } = useBusiness();
  const update = useUpdateBusiness();
  const [form, setForm] = useState<BusinessInput | null>(null);
  const [logoUrl, setLogoUrl] = useState<string | null>(null);
  const [coverUrl, setCoverUrl] = useState<string | null>(null);
  const [draft, setDraft] = useState<SiteDocument | null>(null);

  useEffect(() => {
    if (business) {
      setForm(toBusinessInput(business));
      setLogoUrl(business.logoUrl ?? null);
      setCoverUrl(business.coverUrl ?? null);
    }
  }, [business]);

  if (!business || !form) {
    return <LoadingState />;
  }
  const set = (patch: Partial<BusinessInput>) => setForm({ ...form, ...patch });
  const pageUrl = `${window.location.origin}/b/${business.slug}`;

  const save = () =>
    update
      .mutateAsync(form)
      .then(() => toast.success('Business details saved'))
      .catch((err) => toast.error(errorMessage(err)));

  return (
    <Grid container spacing={3}>
      <Grid item xs={12} lg={7}>
        <Stack spacing={3}>
          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography variant="subtitle1" fontWeight={700} gutterBottom>
              Your booking link
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
              Share this link on Instagram, Google or your website. It shows only your business.
            </Typography>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              <TextField fullWidth value={pageUrl} InputProps={{ readOnly: true }} onFocus={(e) => e.target.select()} />
              <Button
                variant="outlined"
                onClick={() =>
                  navigator.clipboard
                    ?.writeText(pageUrl)
                    .then(() => toast.success('Link copied'))
                    .catch(() => toast.error('Copy the link manually'))
                }
              >
                Copy
              </Button>
              <Button variant="contained" endIcon={<LaunchRoundedIcon />} href={pageUrl} target="_blank" rel="noopener">
                Open
              </Button>
            </Stack>
          </Paper>

          <Typography variant="h6" component="h2" fontWeight={700}>
            Business details
          </Typography>

          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography variant="subtitle1" fontWeight={700} gutterBottom>
              Images
            </Typography>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3}>
              <ImageUpload
                label="Logo"
                hint="Square works best."
                url={logoUrl}
                aspectRatio="1 / 1"
                width={120}
                round
                onChange={(media) => {
                  set({ logoMediaId: media?.id ?? null });
                  setLogoUrl(media?.url ?? null);
                }}
              />
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <ImageUpload
                  label="Cover photo"
                  hint="Shown behind your name at the top of the page, unless the top section has photos of its own. A wide photo of your space, at least 1600 px across."
                  url={coverUrl}
                  aspectRatio="16 / 7"
                  width="100%"
                  onChange={(media) => {
                    set({ coverMediaId: media?.id ?? null });
                    setCoverUrl(media?.url ?? null);
                  }}
                />
              </Box>
            </Stack>
          </Paper>

          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography variant="subtitle1" fontWeight={700} gutterBottom>
              Words
            </Typography>
            <Stack spacing={2}>
              <TextField
                label="Tagline"
                placeholder="Classic cuts and shaves — no waiting, on your time"
                value={form.tagline ?? ''}
                onChange={(e) => set({ tagline: e.target.value })}
                inputProps={{ maxLength: 200 }}
                helperText="One line under your name"
              />
              <TextField
                label="About"
                value={form.description ?? ''}
                onChange={(e) => set({ description: e.target.value })}
                multiline
                minRows={3}
                helperText="A short introduction shown on your page"
              />
            </Stack>
          </Paper>

          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography variant="subtitle1" fontWeight={700} gutterBottom>
              Links
            </Typography>
            <Stack spacing={2}>
              <TextField label="Instagram" placeholder="https://instagram.com/yourbusiness" value={form.instagramUrl ?? ''} onChange={(e) => set({ instagramUrl: e.target.value })} />
              <TextField label="Facebook" placeholder="https://facebook.com/yourbusiness" value={form.facebookUrl ?? ''} onChange={(e) => set({ facebookUrl: e.target.value })} />
              <TextField label="Website" placeholder="https://" value={form.websiteUrl ?? ''} onChange={(e) => set({ websiteUrl: e.target.value })} />
            </Stack>
          </Paper>

          <Box>
            <Button variant="contained" size="large" onClick={save} disabled={update.isPending}>
              {update.isPending ? 'Saving…' : 'Save business details'}
            </Button>
          </Box>

          <GalleryManager />

          <Box>
            <Typography variant="h6" component="h2" fontWeight={700}>
              Design
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Changes here are a draft: your customers see them once you publish.
            </Typography>
          </Box>

          <SiteDesigner onDocumentChange={setDraft} />
        </Stack>
      </Grid>

      <Grid item xs={12} lg={5}>
        <Box sx={{ position: { lg: 'sticky' }, top: 88 }}>
          {draft ? (
            <LivePreview
              slug={business.slug}
              document={draft}
              business={{
                name: form.name,
                tagline: form.tagline || null,
                description: form.description || null,
                logoUrl,
                coverUrl,
              }}
            />
          ) : null}
        </Box>
      </Grid>
    </Grid>
  );
}
