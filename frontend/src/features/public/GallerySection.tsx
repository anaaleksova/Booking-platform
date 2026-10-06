import { useCallback, useEffect, useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import { mediaUrl } from '@/features/media/api';
import type { PublicBusiness } from '@/types/api';

type Photo = PublicBusiness['gallery'][number];

/** Up to this many photos sit in a plain grid; more become a carousel. */
const MAX_GRID = 3;

/**
 * The booking page's photo gallery: a grid for up to three photos, a
 * horizontally scrolling carousel for more. The photos are decoration — they
 * are not clickable. Renders nothing when there are no photos.
 */
export function GallerySection({ photos, title }: { photos: Photo[]; title: (eyebrow: string, heading: string) => React.ReactNode }) {
  if (photos.length === 0) {
    return null;
  }
  return (
    <Box id="gallery" sx={{ py: { xs: 6, md: 10 } }}>
      <Container maxWidth="lg">
        {title('Gallery', 'Take a look inside')}

        {photos.length > MAX_GRID ? (
          <Carousel photos={photos} />
        ) : (
          // Up to three photos: a single row that fills the width.
          <Box
            sx={{
              display: { xs: 'none', md: 'grid' },
              gridTemplateColumns: `repeat(${photos.length}, 1fr)`,
              gridAutoRows: photos.length <= 2 ? 360 : 260,
              gap: 1.5,
            }}
          >
            {photos.map((photo) => (
              <Thumb key={photo.url} photo={photo} />
            ))}
          </Box>
        )}

        {/* Phones: one swipeable row that snaps photo by photo. */}
        {photos.length <= MAX_GRID ? (
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
            {photos.map((photo) => (
              <Thumb
                key={photo.url}
                photo={photo}
                sx={{ flex: '0 0 78%', height: 260, scrollSnapAlign: 'center' }}
              />
            ))}
          </Box>
        ) : null}
      </Container>
    </Box>
  );
}

/**
 * More than three photos: a horizontally scrollable row (three visible on wide
 * screens, one and a bit on phones) that snaps photo by photo, with arrows
 * that appear when there is more to see in that direction.
 */
function Carousel({ photos }: { photos: Photo[] }) {
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
  }, [update, photos.length]);

  const scrollBy = (direction: number) => {
    const node = track.current;
    const first = node?.firstElementChild;
    if (node && first) {
      // One photo plus the 12px gap between photos.
      node.scrollBy({ left: direction * (first.getBoundingClientRect().width + 12), behavior: 'smooth' });
    }
  };

  return (
    <Box sx={{ position: 'relative' }}>
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
        {photos.map((photo) => (
          <Thumb
            key={photo.url}
            photo={photo}
            sx={{ flex: { xs: '0 0 78%', md: '0 0 calc((100% - 24px) / 3)' }, height: { xs: 260, md: 280 }, scrollSnapAlign: 'start' }}
          />
        ))}
      </Box>
      {[
        { direction: -1, hidden: edges.start, label: 'Previous photos', side: { left: -20 }, icon: <ChevronLeftRoundedIcon /> },
        { direction: 1, hidden: edges.end, label: 'Next photos', side: { right: -20 }, icon: <ChevronRightRoundedIcon /> },
      ].map(({ direction, hidden, label, side, icon }) => (
        <IconButton
          key={direction}
          onClick={() => scrollBy(direction)}
          aria-label={label}
          sx={{
            display: { xs: 'none', md: 'inline-flex' },
            position: 'absolute',
            top: 'calc(50% - 28px)',
            ...side,
            bgcolor: '#fff',
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

function Thumb({ photo, sx }: { photo: Photo; sx?: object }) {
  return (
    <Box
      sx={{
        position: 'relative',
        borderRadius: 3,
        overflow: 'hidden',
        bgcolor: 'action.hover',
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
    </Box>
  );
}
