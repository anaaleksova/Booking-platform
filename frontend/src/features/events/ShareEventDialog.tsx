import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import ErrorRoundedIcon from '@mui/icons-material/ErrorRounded';
import FacebookRoundedIcon from '@mui/icons-material/FacebookRounded';
import InstagramIcon from '@mui/icons-material/Instagram';
import { errorMessage } from '@/api/client';
import { useToast } from '@/components/ToastProvider';
import { useEventSharePreview, useShareEvent } from '@/features/events/api';
import type { BusinessEvent, EventSocialPost } from '@/types/api';

const CAPTION_LIMIT = 2200;
const NAMES = { FACEBOOK: 'Facebook', INSTAGRAM: 'Instagram' } as const;

/**
 * Posts an event on the business's connected Facebook Page and Instagram
 * account. The caption starts from a suggestion the owner edits; each
 * platform succeeds or fails on its own, with Meta's reason shown.
 */
export function ShareEventDialog({
  event,
  intro,
  onClose,
}: {
  event: BusinessEvent | null;
  /** A line above the form, e.g. after the event was just created. */
  intro?: string;
  onClose: () => void;
}) {
  const toast = useToast();
  const { data: preview, isLoading, isError, error } = useEventSharePreview(event?.id ?? null);
  const share = useShareEvent();
  const [facebook, setFacebook] = useState(false);
  const [instagram, setInstagram] = useState(false);
  const [caption, setCaption] = useState('');
  const [results, setResults] = useState<EventSocialPost[] | null>(null);

  useEffect(() => {
    if (preview) {
      setCaption(preview.suggestedCaption);
      setFacebook(!preview.facebookUnavailable);
      setInstagram(!preview.instagramUnavailable);
    }
  }, [preview]);

  useEffect(() => {
    if (!event) setResults(null);
  }, [event]);

  const notConnected = preview?.facebookUnavailable != null;

  const post = async () => {
    if (!event) return;
    try {
      const result = await share.mutateAsync({ id: event.id, input: { facebook, instagram, caption: caption.trim() } });
      setResults(result.posts);
      // A refusal is shown in the dialog with Meta's reason; only full success gets a toast.
      if (result.posts.every((p) => p.status === 'POSTED')) toast.success('Posted');
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <Dialog open={event !== null} onClose={share.isPending ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Share “{event?.title}”</DialogTitle>
      <DialogContent>
        {intro ? (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {intro}
          </Typography>
        ) : null}
        {isError ? <Alert severity="error">{errorMessage(error)}</Alert> : null}
        {isLoading || !preview ? (
          isError ? null : (
            <Stack alignItems="center" sx={{ py: 4 }}>
              <CircularProgress size={28} />
            </Stack>
          )
        ) : results ? (
          <Stack spacing={1.5}>
            {results.map((result) => (
              <PostLine key={result.platform} post={result} />
            ))}
          </Stack>
        ) : notConnected ? (
          <Alert severity="info">
            Connect your Facebook Page first, then you can post events with one click.{' '}
            <Link component={RouterLink} to="/admin/integrations">
              Go to Integrations
            </Link>
          </Alert>
        ) : (
          <>
            <Stack spacing={0.5} sx={{ mb: 2 }}>
              <PlatformChoice
                icon={<FacebookRoundedIcon sx={{ color: '#1877F2' }} />}
                label={`Facebook · ${preview.pageName ?? 'your Page'}`}
                checked={facebook}
                unavailable={preview.facebookUnavailable}
                onChange={setFacebook}
              />
              <PlatformChoice
                icon={<InstagramIcon sx={{ color: '#C13584' }} />}
                label={preview.instagramUsername ? `Instagram · @${preview.instagramUsername}` : 'Instagram'}
                checked={instagram}
                unavailable={preview.instagramUnavailable}
                onChange={setInstagram}
              />
            </Stack>
            <TextField
              fullWidth
              multiline
              minRows={6}
              label="Caption"
              value={caption}
              onChange={(e) => setCaption(e.target.value)}
              error={caption.length > CAPTION_LIMIT}
              helperText={`${caption.length} / ${CAPTION_LIMIT}${event?.imageUrl ? ' · the event picture is posted with it' : ''}`}
            />
            {preview.posts.length > 0 ? (
              <Stack spacing={1} sx={{ mt: 2 }}>
                <Typography variant="subtitle2" color="text.secondary">
                  Shared before
                </Typography>
                {preview.posts.slice(0, 4).map((p) => (
                  <PostLine key={`${p.platform}-${p.createdAt}`} post={p} compact />
                ))}
              </Stack>
            ) : null}
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={share.isPending}>
          {results || notConnected ? 'Close' : 'Not now'}
        </Button>
        {!results && preview && !notConnected ? (
          <Button
            variant="contained"
            onClick={post}
            disabled={(!facebook && !instagram) || !caption.trim() || caption.length > CAPTION_LIMIT || share.isPending}
            startIcon={share.isPending ? <CircularProgress size={16} color="inherit" /> : undefined}
          >
            {share.isPending ? 'Posting…' : 'Post now'}
          </Button>
        ) : null}
      </DialogActions>
    </Dialog>
  );
}

function PlatformChoice({
  icon,
  label,
  checked,
  unavailable,
  onChange,
}: {
  icon: JSX.Element;
  label: string;
  checked: boolean;
  unavailable?: string | null;
  onChange: (checked: boolean) => void;
}) {
  return (
    <div>
      <FormControlLabel
        disabled={!!unavailable}
        control={<Checkbox checked={checked && !unavailable} onChange={(e) => onChange(e.target.checked)} />}
        label={
          <Stack direction="row" spacing={1} alignItems="center">
            {icon}
            <span>{label}</span>
          </Stack>
        }
      />
      {unavailable ? (
        <Typography variant="caption" color="text.secondary" display="block" sx={{ ml: 4, mt: -0.5 }}>
          {unavailable}
        </Typography>
      ) : null}
    </div>
  );
}

function PostLine({ post, compact = false }: { post: EventSocialPost; compact?: boolean }) {
  const ok = post.status === 'POSTED';
  return (
    <Stack direction="row" spacing={1} alignItems="flex-start">
      {ok ? (
        <CheckCircleRoundedIcon color="success" fontSize="small" sx={{ mt: 0.25 }} />
      ) : (
        <ErrorRoundedIcon color="error" fontSize="small" sx={{ mt: 0.25 }} />
      )}
      <div>
        <Typography variant="body2" fontWeight={compact ? 400 : 600}>
          {ok ? `Posted on ${NAMES[post.platform]}` : `Not posted on ${NAMES[post.platform]}`}
          {compact ? ` · ${new Date(post.createdAt).toLocaleString()}` : ''}
          {ok && post.permalink ? (
            <>
              {' · '}
              <Link href={post.permalink} target="_blank" rel="noopener noreferrer">
                View post
              </Link>
            </>
          ) : null}
        </Typography>
        {!ok && post.error ? (
          <Typography variant="caption" color="text.secondary">
            {post.error}
          </Typography>
        ) : null}
      </div>
    </Stack>
  );
}
