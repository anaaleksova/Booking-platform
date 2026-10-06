import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded';
import { errorMessage } from '@/api/client';
import { ImageUpload } from '@/components/ImageUpload';
import { LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { toBusinessInput, useBusiness, useUpdateBusiness, type BusinessInput } from '@/features/business/api';
import { mediaUrl } from '@/features/media/api';
import { GalleryManager } from './GalleryManager';

/** Brand colours that read well with white text on buttons. */
const SWATCHES = ['#2f5bea', '#1f6f8b', '#0f9d76', '#3d7a3a', '#b4536a', '#d23f63', '#c2551d', '#7a4fd6', '#111827'];

/**
 * How the business looks to its customers: images, words, colour and what
 * the page shows. Only this business's page is ever reachable from its link.
 */
export function BookingPageTab() {
  const toast = useToast();
  const { data: business } = useBusiness();
  const update = useUpdateBusiness();
  const [form, setForm] = useState<BusinessInput | null>(null);
  const [logoUrl, setLogoUrl] = useState<string | null>(null);
  const [coverUrl, setCoverUrl] = useState<string | null>(null);

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
      .then(() => toast.success('Booking page saved'))
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
                  hint="Shown behind your name at the top of the page. A wide photo of your space, at least 1600 px across."
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

          <GalleryManager show={form.showGallery} onShowChange={(showGallery) => set({ showGallery })} />

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
              Colour
            </Typography>
            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
              {SWATCHES.map((color) => (
                <ButtonBase
                  key={color}
                  aria-label={`Use colour ${color}`}
                  onClick={() => set({ accentColor: color })}
                  sx={{
                    width: 36,
                    height: 36,
                    borderRadius: '50%',
                    bgcolor: color,
                    outline: form.accentColor.toLowerCase() === color ? '3px solid' : 'none',
                    outlineColor: 'text.primary',
                    outlineOffset: 2,
                  }}
                />
              ))}
              <TextField
                type="color"
                label="Custom"
                value={form.accentColor}
                onChange={(e) => set({ accentColor: e.target.value })}
                sx={{ width: 96 }}
              />
            </Stack>
          </Paper>

          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography variant="subtitle1" fontWeight={700} gutterBottom>
              Links and sections
            </Typography>
            <Stack spacing={2}>
              <TextField label="Instagram" placeholder="https://instagram.com/yourbusiness" value={form.instagramUrl ?? ''} onChange={(e) => set({ instagramUrl: e.target.value })} />
              <TextField label="Facebook" placeholder="https://facebook.com/yourbusiness" value={form.facebookUrl ?? ''} onChange={(e) => set({ facebookUrl: e.target.value })} />
              <TextField label="Website" placeholder="https://" value={form.websiteUrl ?? ''} onChange={(e) => set({ websiteUrl: e.target.value })} />
              <FormControlLabel
                control={<Switch checked={form.showMap} onChange={(e) => set({ showMap: e.target.checked })} />}
                label="Show a map of your address"
              />
              <FormControlLabel
                control={<Switch checked={form.darkTheme} onChange={(e) => set({ darkTheme: e.target.checked })} />}
                label="Use a black background on your booking pages"
              />
            </Stack>
          </Paper>

          <Box>
            <Button variant="contained" size="large" onClick={save} disabled={update.isPending}>
              {update.isPending ? 'Saving…' : 'Save booking page'}
            </Button>
          </Box>
        </Stack>
      </Grid>

      <Grid item xs={12} lg={5}>
        <Box sx={{ position: { lg: 'sticky' }, top: 88 }}>
          <Typography variant="overline" color="text.secondary">
            Preview
          </Typography>
          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Box
              sx={{
                position: 'relative',
                minHeight: 220,
                display: 'grid',
                placeItems: 'center',
                textAlign: 'center',
                color: '#fff',
                p: 3,
                backgroundColor: form.accentColor,
                backgroundImage: coverUrl
                  ? `linear-gradient(rgba(10,12,20,0.45), rgba(10,12,20,0.65)), url("${mediaUrl(coverUrl)}")`
                  : `linear-gradient(135deg, ${form.accentColor}, #111827)`,
                backgroundSize: 'cover',
                backgroundPosition: 'center',
              }}
            >
              <Stack alignItems="center" spacing={1}>
                {logoUrl ? (
                  <Box component="img" src={mediaUrl(logoUrl)} alt="" sx={{ width: 56, height: 56, borderRadius: '50%', objectFit: 'cover', border: '2px solid #fff' }} />
                ) : null}
                <Typography variant="h5" fontWeight={800}>
                  {form.name}
                </Typography>
                {form.tagline ? <Typography variant="body2" sx={{ opacity: 0.9 }}>{form.tagline}</Typography> : null}
                <Box sx={{ mt: 1, px: 2.5, py: 1, borderRadius: 999, bgcolor: form.accentColor, fontWeight: 700, fontSize: 14 }}>
                  Book an appointment
                </Box>
              </Stack>
            </Box>
            <Box sx={{ p: 2 }}>
              <Typography variant="caption" color="text.secondary">
                Save, then open your page to see it in full.
              </Typography>
            </Box>
          </Paper>
        </Box>
      </Grid>
    </Grid>
  );
}
