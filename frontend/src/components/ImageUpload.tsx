import { useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import AddPhotoAlternateRoundedIcon from '@mui/icons-material/AddPhotoAlternateRounded';
import { errorMessage } from '@/api/client';
import { useToast } from '@/components/ToastProvider';
import { mediaUrl, uploadImage } from '@/features/media/api';

const ACCEPT = 'image/jpeg,image/png,image/webp,image/gif';
const MAX_BYTES = 5 * 1024 * 1024;

/**
 * Pick, preview and replace one image. The file is uploaded straight away;
 * the returned id is what the surrounding form saves.
 */
export function ImageUpload({
  label,
  hint,
  url,
  onChange,
  aspectRatio = '4 / 3',
  width = 200,
  round = false,
}: {
  label: string;
  hint?: string;
  /** The current image URL, if any. */
  url?: string | null;
  onChange: (media: { id: string; url: string } | null) => void;
  aspectRatio?: string;
  width?: number | string;
  round?: boolean;
}) {
  const input = useRef<HTMLInputElement>(null);
  const toast = useToast();
  const [uploading, setUploading] = useState(false);

  const pick = async (file: File | undefined) => {
    if (!file) {
      return;
    }
    if (file.size > MAX_BYTES) {
      toast.error('Images can be at most 5 MB.');
      return;
    }
    setUploading(true);
    try {
      const uploaded = await uploadImage(file);
      onChange({ id: uploaded.id, url: uploaded.url });
    } catch (error) {
      toast.error(errorMessage(error, 'The image could not be uploaded.'));
    } finally {
      setUploading(false);
      if (input.current) {
        input.current.value = '';
      }
    }
  };

  return (
    <Stack spacing={1}>
      <Typography variant="subtitle2">{label}</Typography>
      <Box
        role="button"
        tabIndex={0}
        aria-label={url ? `Replace ${label}` : `Upload ${label}`}
        onClick={() => input.current?.click()}
        onKeyDown={(event) => (event.key === 'Enter' || event.key === ' ') && input.current?.click()}
        sx={{
          width,
          aspectRatio,
          borderRadius: round ? '50%' : 2,
          border: 1,
          borderStyle: url ? 'solid' : 'dashed',
          borderColor: 'divider',
          bgcolor: 'background.default',
          backgroundImage: url ? `url("${mediaUrl(url)}")` : undefined,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
          display: 'grid',
          placeItems: 'center',
          cursor: 'pointer',
          overflow: 'hidden',
          '&:hover': { borderColor: 'primary.main' },
        }}
      >
        {uploading ? (
          <CircularProgress size={24} />
        ) : url ? null : (
          <Stack alignItems="center" spacing={0.5} sx={{ color: 'text.secondary', p: 1, textAlign: 'center' }}>
            <AddPhotoAlternateRoundedIcon />
            <Typography variant="caption">Upload</Typography>
          </Stack>
        )}
      </Box>
      <input ref={input} type="file" accept={ACCEPT} hidden onChange={(event) => pick(event.target.files?.[0])} />
      <Stack direction="row" spacing={1}>
        <Button size="small" onClick={() => input.current?.click()} disabled={uploading}>
          {url ? 'Replace' : 'Choose image'}
        </Button>
        {url ? (
          <Button size="small" color="error" onClick={() => onChange(null)} disabled={uploading}>
            Remove
          </Button>
        ) : null}
      </Stack>
      {hint ? (
        <Typography variant="caption" color="text.secondary">
          {hint}
        </Typography>
      ) : null}
    </Stack>
  );
}
