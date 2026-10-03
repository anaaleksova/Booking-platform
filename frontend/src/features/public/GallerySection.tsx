import { useCallback, useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Container from '@mui/material/Container';
import Dialog from '@mui/material/Dialog';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import { mediaUrl } from '@/features/media/api';
import type { PublicBusiness } from '@/types/api';

type Photo = PublicBusiness['gallery'][number];

/**
 * The booking page's photo gallery: a grid on wide screens, a swipeable row on
 * phones, and a full-screen viewer with on-screen and keyboard arrows
 * and captions. Renders nothing when there are no photos.
 */
export function GallerySection({ photos, title }: { photos: Photo[]; title: (eyebrow: string, heading: string) => React.ReactNode }) {
  const [open, setOpen] = useState<number | null>(null);
  const layout = gridLayout(photos.length);

  if (photos.length === 0) {
    return null;
  }
  return (
    <Box id="gallery" sx={{ py: { xs: 6, md: 10 } }}>
      <Container maxWidth="lg">
        {title('Gallery', 'Take a look inside')}

        {/* Wide screens: a grid sized to the number of photos, so rows end even. */}
        <Box
          sx={{
            display: { xs: 'none', md: 'grid' },
            gridTemplateColumns: `repeat(${layout.columns}, 1fr)`,
            gridAutoRows: layout.rowHeight,
            gap: 1.5,
          }}
        >
          {photos.map((photo, index) => (
            <Thumb
              key={photo.url}
              photo={photo}
              onClick={() => setOpen(index)}
              sx={index === 0 && layout.leadPhoto ? { gridColumn: 'span 2', gridRow: 'span 2' } : undefined}
            />
          ))}
        </Box>

        {/* Phones: one swipeable row that snaps photo by photo. */}
        <Box
          sx={{
            display: { xs: 'flex', md: 'none' },
            gap: 1.5,
            overflowX: 'auto',
            scrollSnapType: 'x mandatory',
            mx: -2,
            px: 2,
            pb: 1,
            scrollbarWidth: 'none',
          }}
        >
          {photos.map((photo, index) => (
            <Thumb
              key={photo.url}
              photo={photo}
              onClick={() => setOpen(index)}
              sx={{ flex: '0 0 78%', height: 260, scrollSnapAlign: 'center' }}
            />
          ))}
        </Box>
      </Container>

      <Viewer photos={photos} index={open} onChange={setOpen} />
    </Box>
  );
}

/**
 * Columns for the desktop grid. A large lead photo (2×2) is used only when the
 * remaining photos still fill the rows exactly — 5 or 9 photos in 4 columns;
 * otherwise the column count is picked to divide the photos evenly.
 */
function gridLayout(count: number): { columns: number; leadPhoto: boolean; rowHeight: number } {
  if (count === 5 || count === 9) {
    return { columns: 4, leadPhoto: true, rowHeight: 200 };
  }
  if (count <= 4) {
    return { columns: Math.max(1, count), leadPhoto: false, rowHeight: count <= 2 ? 360 : 260 };
  }
  const columns = count % 4 === 0 ? 4 : count % 3 === 0 ? 3 : count % 5 === 0 ? 5 : 4;
  return { columns, leadPhoto: false, rowHeight: columns === 3 ? 260 : 220 };
}

function Thumb({ photo, onClick, sx }: { photo: Photo; onClick: () => void; sx?: object }) {
  return (
    <ButtonBase
      onClick={onClick}
      aria-label={photo.caption ? `Open photo: ${photo.caption}` : 'Open photo'}
      sx={{
        position: 'relative',
        borderRadius: 3,
        overflow: 'hidden',
        bgcolor: 'action.hover',
        '& img': { transition: 'transform 300ms ease' },
        '&:hover img': { transform: 'scale(1.04)' },
        ...sx,
      }}
    >
      <Box
        component="img"
        src={mediaUrl(photo.url)}
        alt={photo.caption ?? ''}
        loading="lazy"
        decoding="async"
        sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
      />
      {photo.caption ? (
        <Typography
          variant="caption"
          sx={{
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 0,
            px: 1.5,
            py: 1,
            color: '#fff',
            textAlign: 'left',
            fontWeight: 600,
            background: 'linear-gradient(transparent, rgba(0,0,0,0.6))',
          }}
        >
          {photo.caption}
        </Typography>
      ) : null}
    </ButtonBase>
  );
}

function Viewer({ photos, index, onChange }: { photos: Photo[]; index: number | null; onChange: (index: number | null) => void }) {
  const step = useCallback(
    (direction: number) => index !== null && onChange((index + direction + photos.length) % photos.length),
    [index, onChange, photos.length],
  );

  useEffect(() => {
    if (index === null) {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'ArrowRight') step(1);
      if (event.key === 'ArrowLeft') step(-1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [index, step]);

  const photo = index === null ? null : photos[index];
  return (
    <Dialog
      open={photo !== null}
      onClose={() => onChange(null)}
      fullScreen
      PaperProps={{ sx: { bgcolor: 'rgba(8,9,12,0.96)', color: '#fff' } }}
    >
      {photo ? (
        <Stack sx={{ height: '100%' }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ p: 1.5 }}>
            <Typography variant="body2" sx={{ opacity: 0.7, pl: 1 }}>
              {(index ?? 0) + 1} / {photos.length}
            </Typography>
            <IconButton onClick={() => onChange(null)} aria-label="Close" sx={{ color: '#fff' }}>
              <CloseRoundedIcon />
            </IconButton>
          </Stack>
          <Box sx={{ flex: 1, minHeight: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative', px: { xs: 0, md: 9 } }}>
            {photos.length > 1 ? (
              <IconButton onClick={() => step(-1)} aria-label="Previous photo" sx={{ position: 'absolute', left: 8, color: '#fff', bgcolor: 'rgba(255,255,255,0.12)', '&:hover': { bgcolor: 'rgba(255,255,255,0.2)' } }}>
                <ChevronLeftRoundedIcon fontSize="large" />
              </IconButton>
            ) : null}
            <Box component="img" src={mediaUrl(photo.url)} alt={photo.caption ?? ''} sx={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
            {photos.length > 1 ? (
              <IconButton onClick={() => step(1)} aria-label="Next photo" sx={{ position: 'absolute', right: 8, color: '#fff', bgcolor: 'rgba(255,255,255,0.12)', '&:hover': { bgcolor: 'rgba(255,255,255,0.2)' } }}>
                <ChevronRightRoundedIcon fontSize="large" />
              </IconButton>
            ) : null}
          </Box>
          <Typography sx={{ textAlign: 'center', p: 2, minHeight: 56, opacity: 0.9 }}>{photo.caption}</Typography>
        </Stack>
      ) : null}
    </Dialog>
  );
}
