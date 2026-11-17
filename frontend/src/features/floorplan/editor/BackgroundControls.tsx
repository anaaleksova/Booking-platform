import { useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import FormControlLabel from '@mui/material/FormControlLabel';
import Slider from '@mui/material/Slider';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import AddPhotoAlternateRoundedIcon from '@mui/icons-material/AddPhotoAlternateRounded';
import { errorMessage } from '@/api/client';
import { useToast } from '@/components/ToastProvider';
import { mediaUrl, uploadImage } from '@/features/media/api';
import { aspectOf, type Background } from './editorModel';

const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
const MAX_BYTES = 5 * 1024 * 1024;
/** Plenty to trace over; phone photos are scaled down to this. */
const MAX_SIDE = 2400;

/** How the picture is shown while editing; not saved. */
export interface BackdropView {
  visible: boolean;
  opacity: number;
}

/**
 * "Trace your floor plan": a photo or drawing of the place, shown faintly
 * behind the floor so walls and tables can be drawn over it.
 */
export function BackgroundControls({
  background,
  view,
  onViewChange,
  onChange,
}: {
  background: Background | null;
  view: BackdropView;
  onViewChange: (view: BackdropView) => void;
  /** A new picture comes with its shape, so the room can take it. */
  onChange: (background: Background | null, aspectRatio?: number) => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const toast = useToast();
  const [uploading, setUploading] = useState(false);

  const pick = async (file: File | undefined) => {
    if (!file) {
      return;
    }
    if (!ACCEPTED.includes(file.type)) {
      toast.error('Use a JPG, PNG, WebP or GIF picture. A PDF plan can be saved as an image first.');
      return;
    }
    setUploading(true);
    try {
      const prepared = await prepare(file);
      const uploaded = await uploadImage(prepared.file);
      onChange({ mediaId: uploaded.id, url: uploaded.url }, aspectOf(prepared.width, prepared.height));
      onViewChange({ ...view, visible: true });
    } catch (error) {
      toast.error(
        error instanceof PictureProblem ? error.message : errorMessage(error, 'The picture could not be uploaded.'),
      );
    } finally {
      setUploading(false);
      if (input.current) {
        input.current.value = '';
      }
    }
  };

  const fileInput = (
    <input ref={input} type="file" accept={ACCEPTED.join(',')} hidden onChange={(event) => pick(event.target.files?.[0])} />
  );

  if (!background) {
    return (
      <section>
        <Typography variant="subtitle2" fontWeight={700} gutterBottom>
          Trace your floor plan
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          Upload a photo or drawing of the place. It shows faintly behind this floor, so you can put the walls and tables
          right on top of it. Customers don’t see it.
        </Typography>
        <Button
          variant="outlined"
          size="small"
          startIcon={uploading ? <CircularProgress size={16} /> : <AddPhotoAlternateRoundedIcon />}
          onClick={() => input.current?.click()}
          disabled={uploading}
        >
          {uploading ? 'Uploading…' : 'Upload plan image'}
        </Button>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
          Crop it to the room’s outer walls first: the floor takes the picture’s shape.
        </Typography>
        {fileInput}
      </section>
    );
  }

  return (
    <section>
      <Typography variant="subtitle2" fontWeight={700} gutterBottom>
        Floor plan image
      </Typography>
      <Stack direction="row" spacing={1.5} alignItems="center">
        <Box
          component="img"
          src={mediaUrl(background.url)}
          alt="Uploaded floor plan"
          sx={{ width: 64, height: 48, objectFit: 'cover', borderRadius: 1, border: 1, borderColor: 'divider', flexShrink: 0 }}
        />
        <FormControlLabel
          control={
            <Switch size="small" checked={view.visible} onChange={(event) => onViewChange({ ...view, visible: event.target.checked })} />
          }
          label={<Typography variant="body2">Show behind the map</Typography>}
        />
      </Stack>
      <Typography variant="caption" color="text.secondary" id="backdrop-strength" sx={{ display: 'block', mt: 1 }}>
        How strongly it shows
      </Typography>
      <Slider
        size="small"
        aria-labelledby="backdrop-strength"
        min={0.15}
        max={1}
        step={0.05}
        value={view.opacity}
        disabled={!view.visible}
        valueLabelDisplay="auto"
        valueLabelFormat={(value) => `${Math.round(value * 100)}%`}
        onChange={(_, value) => onViewChange({ ...view, opacity: value as number })}
      />
      <Stack direction="row" spacing={1}>
        <Button size="small" onClick={() => input.current?.click()} disabled={uploading} startIcon={uploading ? <CircularProgress size={14} /> : undefined}>
          Replace
        </Button>
        <Button size="small" color="error" onClick={() => onChange(null)} disabled={uploading}>
          Remove
        </Button>
      </Stack>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
        Only you see it, while editing.
      </Typography>
      {fileInput}
    </section>
  );
}

/** A picture the browser could not read or shrink; its message is for the owner. */
class PictureProblem extends Error {}

/** The picture's size, scaled down (as a JPEG) when it is too big to upload or larger than needed. */
async function prepare(file: File): Promise<{ file: File; width: number; height: number }> {
  const url = URL.createObjectURL(file);
  try {
    const image = await load(url);
    const { naturalWidth: width, naturalHeight: height } = image;
    const scale = Math.min(1, MAX_SIDE / Math.max(width, height));
    if (scale === 1 && file.size <= MAX_BYTES) {
      return { file, width, height };
    }
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(width * scale);
    canvas.height = Math.round(height * scale);
    const context = canvas.getContext('2d');
    if (!context) {
      throw new PictureProblem('This browser cannot resize the picture. Try one under 5 MB.');
    }
    // Transparent drawings stay readable on white.
    context.fillStyle = '#fff';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(image, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85));
    if (!blob || blob.size > MAX_BYTES) {
      throw new PictureProblem('The picture is too large. Try one under 5 MB.');
    }
    const name = file.name.replace(/\.[^.]+$/, '') + '.jpg';
    return { file: new File([blob], name, { type: 'image/jpeg' }), width, height };
  } finally {
    URL.revokeObjectURL(url);
  }
}

function load(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new PictureProblem('The picture could not be read. Try a JPG or PNG.'));
    image.src = url;
  });
}
