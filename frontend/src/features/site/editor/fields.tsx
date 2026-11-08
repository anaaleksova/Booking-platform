import { useRef, useState, type ReactNode } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddPhotoAlternateRoundedIcon from '@mui/icons-material/AddPhotoAlternateRounded';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import ArrowDownwardRoundedIcon from '@mui/icons-material/ArrowDownwardRounded';
import ArrowUpwardRoundedIcon from '@mui/icons-material/ArrowUpwardRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import { errorMessage } from '@/api/client';
import { useToast } from '@/components/ToastProvider';
import { mediaUrl, uploadImage } from '@/features/media/api';
import { siteImageUrl } from '../api';
import type { ButtonAction, Section, SiteButton, SiteImage } from '../types';

const ACCEPT = 'image/jpeg,image/png,image/webp,image/gif';
const MAX_BYTES = 5 * 1024 * 1024;

/** A text field where empty means "use the default", which the placeholder shows. */
export function OptionalText({
  label,
  value,
  placeholder,
  onChange,
  maxLength,
  multiline = false,
  helperText,
}: {
  label: string;
  value?: string | null;
  placeholder?: string;
  onChange: (value: string | null) => void;
  maxLength: number;
  multiline?: boolean;
  helperText?: string;
}) {
  return (
    <TextField
      label={label}
      value={value ?? ''}
      placeholder={placeholder}
      onChange={(event) => onChange(event.target.value === '' ? null : event.target.value)}
      inputProps={{ maxLength }}
      InputLabelProps={placeholder ? { shrink: true } : undefined}
      multiline={multiline}
      minRows={multiline ? 3 : undefined}
      helperText={helperText}
      fullWidth
    />
  );
}

/** Opens the file picker and uploads what was picked, one image or several. */
function useImagePicker(onUploaded: (images: SiteImage[]) => void) {
  const input = useRef<HTMLInputElement>(null);
  const toast = useToast();
  const [uploading, setUploading] = useState(false);

  const pick = async (files: FileList | null) => {
    const chosen = [...(files ?? [])];
    if (chosen.some((file) => file.size > MAX_BYTES)) {
      toast.error('Images can be at most 5 MB.');
      return;
    }
    setUploading(true);
    try {
      const uploaded = await Promise.all(chosen.map((file) => uploadImage(file)));
      onUploaded(uploaded.map((media) => ({ mediaId: media.id, alt: '' })));
    } catch (error) {
      toast.error(errorMessage(error, 'The image could not be uploaded.'));
    } finally {
      setUploading(false);
      if (input.current) {
        input.current.value = '';
      }
    }
  };

  const element = (multiple: boolean) => (
    <input ref={input} type="file" accept={ACCEPT} multiple={multiple} hidden onChange={(event) => pick(event.target.files)} />
  );
  return { open: () => input.current?.click(), uploading, element };
}

/** One image with its description; removing it shows the default instead. */
export function ImageField({
  label,
  image,
  onChange,
  emptyHint,
}: {
  label: string;
  image?: SiteImage | null;
  onChange: (image: SiteImage | null) => void;
  emptyHint?: string;
}) {
  const picker = useImagePicker((images) => onChange(images[0] ?? null));
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        {label}
      </Typography>
      {image ? (
        <ImageRow image={image} onChange={onChange} onRemove={() => onChange(null)} />
      ) : (
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Button variant="outlined" startIcon={picker.uploading ? <CircularProgress size={16} /> : <AddPhotoAlternateRoundedIcon />} onClick={picker.open} disabled={picker.uploading}>
            Choose a photo
          </Button>
          {emptyHint ? (
            <Typography variant="caption" color="text.secondary">
              {emptyHint}
            </Typography>
          ) : null}
        </Stack>
      )}
      {picker.element(false)}
    </Box>
  );
}

/** Several images in order, each with its description. */
export function ImagesField({
  label,
  images,
  onChange,
  max,
  emptyHint,
}: {
  label: string;
  images: SiteImage[];
  onChange: (images: SiteImage[]) => void;
  max: number;
  emptyHint?: string;
}) {
  const picker = useImagePicker((added) => onChange([...images, ...added].slice(0, max)));
  const move = (index: number, by: number) => {
    const next = [...images];
    [next[index], next[index + by]] = [next[index + by], next[index]];
    onChange(next);
  };
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        {label}
      </Typography>
      <Stack spacing={1}>
        {images.map((image, index) => (
          <ImageRow
            key={`${image.mediaId}-${index}`}
            image={image}
            onChange={(changed) => onChange(images.map((current, i) => (i === index ? changed : current)))}
            onRemove={() => onChange(images.filter((_, i) => i !== index))}
            onMoveUp={index > 0 ? () => move(index, -1) : undefined}
            onMoveDown={index < images.length - 1 ? () => move(index, 1) : undefined}
          />
        ))}
      </Stack>
      <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mt: 1 }}>
        <Button
          variant="outlined"
          startIcon={picker.uploading ? <CircularProgress size={16} /> : <AddPhotoAlternateRoundedIcon />}
          onClick={picker.open}
          disabled={picker.uploading || images.length >= max}
        >
          Add photos
        </Button>
        {images.length === 0 && emptyHint ? (
          <Typography variant="caption" color="text.secondary">
            {emptyHint}
          </Typography>
        ) : null}
      </Stack>
      {picker.element(true)}
    </Box>
  );
}

function ImageRow({
  image,
  onChange,
  onRemove,
  onMoveUp,
  onMoveDown,
}: {
  image: SiteImage;
  onChange: (image: SiteImage) => void;
  onRemove: () => void;
  onMoveUp?: () => void;
  onMoveDown?: () => void;
}) {
  return (
    <Stack direction="row" spacing={1.5} alignItems="center">
      <Box component="img" src={mediaUrl(siteImageUrl(image.mediaId))} alt="" sx={{ width: 64, height: 48, objectFit: 'cover', borderRadius: 1, flexShrink: 0 }} />
      <TextField
        size="small"
        label="Description for screen readers"
        placeholder="e.g. The terrace at sunset"
        value={image.alt ?? ''}
        onChange={(event) => onChange({ ...image, alt: event.target.value })}
        inputProps={{ maxLength: 200 }}
        fullWidth
      />
      {onMoveUp || onMoveDown ? (
        <>
          <IconButton size="small" aria-label="Move photo up" onClick={onMoveUp} disabled={!onMoveUp}>
            <ArrowUpwardRoundedIcon fontSize="small" />
          </IconButton>
          <IconButton size="small" aria-label="Move photo down" onClick={onMoveDown} disabled={!onMoveDown}>
            <ArrowDownwardRoundedIcon fontSize="small" />
          </IconButton>
        </>
      ) : null}
      <IconButton size="small" aria-label="Remove photo" onClick={onRemove}>
        <DeleteOutlineRoundedIcon fontSize="small" />
      </IconButton>
    </Stack>
  );
}

const ACTION_LABELS: Record<ButtonAction, string> = {
  BOOKING: 'Open booking',
  SECTION: 'Scroll to a section',
  URL: 'Open a link',
};

/** Buttons of a section: label, what they do and where to. */
export function ButtonsField({
  buttons,
  onChange,
  sections,
  max,
}: {
  buttons: SiteButton[];
  onChange: (buttons: SiteButton[]) => void;
  /** The page's sections, to scroll to. */
  sections: Array<{ id: string; label: string }>;
  max: number;
}) {
  const set = (index: number, patch: Partial<SiteButton>) =>
    onChange(buttons.map((button, i) => (i === index ? { ...button, ...patch } : button)));
  return (
    <ListField
      label="Buttons"
      items={buttons}
      max={max}
      addLabel="Add a button"
      onAdd={() => onChange([...buttons, { label: 'Book now', action: 'BOOKING', target: null }])}
      onRemove={(index) => onChange(buttons.filter((_, i) => i !== index))}
      render={(button, index) => (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
          <TextField size="small" label="Label" value={button.label} onChange={(event) => set(index, { label: event.target.value })} inputProps={{ maxLength: 40 }} sx={{ flex: 1 }} />
          <TextField
            select
            size="small"
            label="Does"
            value={button.action}
            onChange={(event) => set(index, { action: event.target.value as ButtonAction, target: null })}
            sx={{ minWidth: 180 }}
          >
            {(Object.keys(ACTION_LABELS) as ButtonAction[]).map((action) => (
              <MenuItem key={action} value={action}>
                {ACTION_LABELS[action]}
              </MenuItem>
            ))}
          </TextField>
          {button.action === 'SECTION' ? (
            <TextField select size="small" label="Section" value={button.target ?? ''} onChange={(event) => set(index, { target: event.target.value })} sx={{ flex: 1 }}>
              {sections.map((section) => (
                <MenuItem key={section.id} value={section.id}>
                  {section.label}
                </MenuItem>
              ))}
            </TextField>
          ) : null}
          {button.action === 'URL' ? (
            <TextField
              size="small"
              label="Link"
              placeholder="https://… , mailto:… or tel:…"
              value={button.target ?? ''}
              onChange={(event) => set(index, { target: event.target.value })}
              inputProps={{ maxLength: 500 }}
              sx={{ flex: 1 }}
            />
          ) : null}
        </Stack>
      )}
    />
  );
}

/** A list of small forms (reviews, numbers, buttons) with add and remove. */
export function ListField<T>({
  label,
  items,
  max,
  addLabel,
  onAdd,
  onRemove,
  render,
}: {
  label: string;
  items: T[];
  max: number;
  addLabel: string;
  onAdd: () => void;
  onRemove: (index: number) => void;
  render: (item: T, index: number) => ReactNode;
}) {
  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        {label}
      </Typography>
      <Stack spacing={1.5}>
        {items.map((item, index) => (
          <Paper key={index} variant="outlined" sx={{ p: 1.5, display: 'flex', gap: 1, alignItems: 'flex-start' }}>
            <Box sx={{ flex: 1, minWidth: 0 }}>{render(item, index)}</Box>
            <IconButton size="small" aria-label={`Remove ${label.toLowerCase()} ${index + 1}`} onClick={() => onRemove(index)}>
              <DeleteOutlineRoundedIcon fontSize="small" />
            </IconButton>
          </Paper>
        ))}
      </Stack>
      <Button startIcon={<AddRoundedIcon />} onClick={onAdd} disabled={items.length >= max} sx={{ mt: 1 }}>
        {addLabel}
      </Button>
    </Box>
  );
}

/** The `content` of a section, updated field by field. */
export type ContentUpdater<S extends Section> = (patch: Partial<S['content']>) => void;
