import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { GallerySection as PhotoGallery } from '@/features/public/GallerySection';
import { mediaUrl } from '@/features/media/api';
import { siteImageUrl } from '../api';
import { useSiteData } from '../render/SiteData';
import type { GallerySection } from '../types';
import { Carousel, SectionBody, SectionHeading, textOr, useImageShape, type SectionProps } from './shared';

interface Photo {
  url: string;
  caption?: string | null;
}

/** The photos to show: the business gallery (its captions describe them), or the images picked for this section. */
function usePhotos(section: GallerySection): Array<Photo & { alt: string }> {
  const { gallery } = useSiteData();
  if (section.content.source === 'GALLERY') {
    return gallery.map((photo) => ({ ...photo, alt: photo.caption ?? '' }));
  }
  return (section.content.images ?? []).map((image) => ({ url: siteImageUrl(image.mediaId), alt: image.alt ?? '' }));
}

function useHeading(section: GallerySection) {
  return {
    eyebrow: textOr(section.content.eyebrow, 'Gallery'),
    title: textOr(section.content.title, 'Take a look inside'),
  };
}

function Photo({ photo, sx }: { photo: Photo & { alt: string }; sx?: object }) {
  const shape = useImageShape();
  return (
    <Box component="figure" sx={{ m: 0, position: 'relative', ...shape(42, { circle: false }), overflow: 'hidden', bgcolor: 'action.hover', ...sx }}>
      <Box component="img" src={mediaUrl(photo.url)} alt={photo.alt} loading="lazy" decoding="async" sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
      {photo.caption ? (
        <Typography
          component="figcaption"
          variant="caption"
          sx={{ position: 'absolute', left: 0, right: 0, bottom: 0, px: 1.5, py: 1, color: '#fff', fontWeight: 600, background: 'linear-gradient(transparent, rgba(0,0,0,0.6))' }}
        >
          {photo.caption}
        </Typography>
      ) : null}
    </Box>
  );
}

/** The gallery the page has always had: a row of up to three, a carousel for more. */
export function GalleryGrid({ section }: SectionProps<GallerySection>) {
  const { eyebrow, title } = useHeading(section);
  const photos = usePhotos(section);
  return <PhotoGallery photos={photos} title={() => <SectionHeading section={section} eyebrow={eyebrow} title={title} />} />;
}

/** Photos of any shape in columns, each at its own height. */
export function GalleryMasonry({ section }: SectionProps<GallerySection>) {
  const shape = useImageShape();
  const { eyebrow, title } = useHeading(section);
  const photos = usePhotos(section);
  if (photos.length === 0) {
    return null;
  }
  return (
    <SectionBody>
      <SectionHeading section={section} eyebrow={eyebrow} title={title} />
      <Box sx={{ columnCount: { xs: 2, md: 3 }, columnGap: 1.5 }}>
        {photos.map((photo, index) => (
          <Box key={`${photo.url}-${index}`} sx={{ breakInside: 'avoid', mb: 1.5 }}>
            <Box component="figure" sx={{ m: 0, position: 'relative', ...shape(42, { circle: false }), overflow: 'hidden' }}>
              <Box component="img" src={mediaUrl(photo.url)} alt={photo.alt} loading="lazy" decoding="async" sx={{ width: '100%', display: 'block' }} />
              {photo.caption ? (
                <Typography component="figcaption" variant="caption" sx={{ position: 'absolute', left: 0, right: 0, bottom: 0, px: 1.5, py: 1, color: '#fff', fontWeight: 600, background: 'linear-gradient(transparent, rgba(0,0,0,0.6))' }}>
                  {photo.caption}
                </Typography>
              ) : null}
            </Box>
          </Box>
        ))}
      </Box>
    </SectionBody>
  );
}

/** Large photos one after another, swiping sideways. */
export function GalleryCarousel({ section }: SectionProps<GallerySection>) {
  const { eyebrow, title } = useHeading(section);
  const photos = usePhotos(section);
  if (photos.length === 0) {
    return null;
  }
  return (
    <SectionBody>
      <SectionHeading section={section} eyebrow={eyebrow} title={title} />
      <Carousel label="Photos" itemWidth={{ xs: '88%', md: 'calc((100% - 12px) / 2)' }}>
        {photos.map((photo, index) => (
          <Photo key={`${photo.url}-${index}`} photo={photo} sx={{ height: { xs: 300, md: 440 } }} />
        ))}
      </Carousel>
    </SectionBody>
  );
}
