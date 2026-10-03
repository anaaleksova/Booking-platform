import { useRef, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import LinearProgress from '@mui/material/LinearProgress';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddPhotoAlternateRoundedIcon from '@mui/icons-material/AddPhotoAlternateRounded';
import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { useToast } from '@/components/ToastProvider';
import {
  useAddGalleryImage,
  useDeleteGalleryImage,
  useGallery,
  useReorderGallery,
  useUpdateGalleryCaption,
} from '@/features/business/api';
import { mediaUrl, uploadImage } from '@/features/media/api';
import type { GalleryImage } from '@/types/api';

const MAX_PHOTOS = 12;
const MAX_BYTES = 5 * 1024 * 1024;

/**
 * The booking-page gallery: add photos (several at once), caption, reorder
 * with the arrows, remove. Changes are saved as they happen; the switch is
 * part of the booking-page form and saved with it.
 */
export function GalleryManager({ show, onShowChange }: { show: boolean; onShowChange: (show: boolean) => void }) {
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const { data: photos = [] } = useGallery();
  const add = useAddGalleryImage();
  const reorder = useReorderGallery();
  const remove = useDeleteGalleryImage();
  const [uploading, setUploading] = useState<{ done: number; total: number } | null>(null);
  const [deleting, setDeleting] = useState<GalleryImage | null>(null);

  const addFiles = async (files: FileList | null) => {
    const room = MAX_PHOTOS - photos.length;
    const selected = Array.from(files ?? []).slice(0, room);
    if (files && files.length > room) {
      toast.notify(`The gallery holds ${MAX_PHOTOS} photos; only the first ${room} were added.`, 'warning');
    }
    setUploading({ done: 0, total: selected.length });
    for (const [index, file] of selected.entries()) {
      try {
        if (file.size > MAX_BYTES) {
          throw new Error(`${file.name} is larger than 5 MB.`);
        }
        const uploaded = await uploadImage(file);
        await add.mutateAsync({ mediaId: uploaded.id });
      } catch (error) {
        toast.error(error instanceof Error && !('response' in error) ? error.message : errorMessage(error));
      }
      setUploading({ done: index + 1, total: selected.length });
    }
    setUploading(null);
    if (input.current) {
      input.current.value = '';
    }
  };

  const move = (index: number, direction: -1 | 1) => {
    const ids = photos.map((photo) => photo.id);
    const target = index + direction;
    [ids[index], ids[target]] = [ids[target], ids[index]];
    reorder.mutateAsync(ids).catch((error) => toast.error(errorMessage(error)));
  };

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2}>
        <div>
          <Typography variant="subtitle1" fontWeight={700}>
            Gallery
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Photos of your work and your space, shown below your services. Up to {MAX_PHOTOS}; the section is hidden
            while it is empty.
          </Typography>
        </div>
        <FormControlLabel
          control={<Switch checked={show} onChange={(event) => onShowChange(event.target.checked)} />}
          label="Show"
          sx={{ flexShrink: 0, mr: 0 }}
        />
      </Stack>

      {uploading ? (
        <Box sx={{ mt: 2 }}>
          <Typography variant="caption" color="text.secondary">
            Uploading {uploading.done} of {uploading.total}…
          </Typography>
          <LinearProgress variant="determinate" value={(uploading.done / Math.max(1, uploading.total)) * 100} />
        </Box>
      ) : null}

      <Box
        sx={{
          mt: 2,
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
          gap: 1.5,
        }}
      >
        {photos.map((photo, index) => (
          <GalleryTile
            key={photo.id}
            photo={photo}
            isFirst={index === 0}
            isLast={index === photos.length - 1}
            onMove={(direction) => move(index, direction)}
            onDelete={() => setDeleting(photo)}
          />
        ))}
        {photos.length < MAX_PHOTOS ? (
          <Box
            role="button"
            tabIndex={0}
            onClick={() => input.current?.click()}
            onKeyDown={(event) => (event.key === 'Enter' || event.key === ' ') && input.current?.click()}
            sx={{
              aspectRatio: '1 / 1',
              borderRadius: 2,
              border: 1,
              borderStyle: 'dashed',
              borderColor: 'divider',
              display: 'grid',
              placeItems: 'center',
              cursor: 'pointer',
              color: 'text.secondary',
              '&:hover': { borderColor: 'primary.main', color: 'primary.main' },
            }}
          >
            <Stack alignItems="center" spacing={0.5}>
              <AddPhotoAlternateRoundedIcon />
              <Typography variant="caption">Add photos</Typography>
            </Stack>
          </Box>
        ) : null}
      </Box>
      <input
        ref={input}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif"
        multiple
        hidden
        onChange={(event) => addFiles(event.target.files)}
      />
      {photos.length === 0 && !uploading ? (
        <Button sx={{ mt: 1.5 }} startIcon={<AddPhotoAlternateRoundedIcon />} onClick={() => input.current?.click()}>
          Choose photos
        </Button>
      ) : null}

      <ConfirmDialog
        open={deleting !== null}
        title="Remove this photo?"
        message="It is removed from your booking page and deleted."
        confirmLabel="Remove"
        busy={remove.isPending}
        onConfirm={() =>
          deleting &&
          remove
            .mutateAsync(deleting.id)
            .catch((error) => toast.error(errorMessage(error)))
            .finally(() => setDeleting(null))
        }
        onClose={() => setDeleting(null)}
      />
    </Paper>
  );
}

function GalleryTile({
  photo,
  isFirst,
  isLast,
  onMove,
  onDelete,
}: {
  photo: GalleryImage;
  isFirst: boolean;
  isLast: boolean;
  onMove: (direction: -1 | 1) => void;
  onDelete: () => void;
}) {
  const toast = useToast();
  const update = useUpdateGalleryCaption();
  const [caption, setCaption] = useState(photo.caption ?? '');

  const saveCaption = () => {
    if (caption.trim() !== (photo.caption ?? '')) {
      update.mutateAsync({ id: photo.id, caption }).catch((error) => toast.error(errorMessage(error)));
    }
  };

  return (
    <Stack spacing={0.75}>
      <Box sx={{ position: 'relative', aspectRatio: '1 / 1', borderRadius: 2, overflow: 'hidden', bgcolor: 'action.hover' }}>
        <Box component="img" src={mediaUrl(photo.url)} alt={photo.caption ?? ''} loading="lazy" sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
        <Stack
          direction="row"
          justifyContent="space-between"
          sx={{ position: 'absolute', left: 4, right: 4, bottom: 4, '& .MuiIconButton-root': { bgcolor: 'rgba(255,255,255,0.9)', '&:hover': { bgcolor: '#fff' } } }}
        >
          <Stack direction="row" spacing={0.5}>
            <Tooltip title="Move earlier">
              <span>
                <IconButton size="small" disabled={isFirst} onClick={() => onMove(-1)} aria-label="Move earlier">
                  <ChevronLeftRoundedIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
            <Tooltip title="Move later">
              <span>
                <IconButton size="small" disabled={isLast} onClick={() => onMove(1)} aria-label="Move later">
                  <ChevronRightRoundedIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          </Stack>
          <Tooltip title="Remove">
            <IconButton size="small" onClick={onDelete} aria-label="Remove photo">
              <DeleteOutlineRoundedIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
      </Box>
      <TextField
        size="small"
        placeholder="Caption (optional)"
        value={caption}
        onChange={(event) => setCaption(event.target.value)}
        onBlur={saveCaption}
        onKeyDown={(event) => event.key === 'Enter' && (event.target as HTMLInputElement).blur()}
        inputProps={{ maxLength: 200 }}
      />
    </Stack>
  );
}
