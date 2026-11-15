import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import Avatar from '@mui/material/Avatar';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import CelebrationRoundedIcon from '@mui/icons-material/CelebrationRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import FacebookRoundedIcon from '@mui/icons-material/FacebookRounded';
import InstagramIcon from '@mui/icons-material/Instagram';
import ShareRoundedIcon from '@mui/icons-material/ShareRounded';
import { errorMessage } from '@/api/client';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ImageUpload } from '@/components/ImageUpload';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, TableLoadingRows } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { useBusiness } from '@/features/business/api';
import { useServices } from '@/features/catalog/api';
import { useDeleteEvent, useEvents, useSaveEvent } from '@/features/events/api';
import { ShareEventDialog } from '@/features/events/ShareEventDialog';
import { useIntegrations } from '@/features/integrations/api';
import { mediaUrl } from '@/features/media/api';
import { offersEvents } from '@/features/onboarding/businessTypes';
import type { BusinessEvent, EventInput, EventState } from '@/types/api';
import { EVENT_STATE_LABELS, formatEventHours, formatLocalDate, formatShortDate } from '@/utils/format';

const STATE_COLORS: Record<EventState, 'success' | 'default' | 'info'> = {
  UPCOMING: 'info',
  SHOWING: 'success',
  PAST: 'default',
};

type Draft = EventInput & { id?: number; imageUrl?: string | null };

const EMPTY: Draft = {
  title: '',
  description: '',
  performer: '',
  imageMediaId: null,
  date: '',
  startTime: '20:00',
  endTime: null,
  showFrom: null,
  showUntil: null,
  serviceId: null,
};

/**
 * Events on the booking page — a live band, a quiz night. Each is shown for a
 * while before it happens and, with a service, can be booked from there.
 */
export function EventsPage() {
  const toast = useToast();
  const { data: services } = useServices();
  const { data: events, isLoading, isError, error, refetch } = useEvents();
  const save = useSaveEvent();
  const remove = useDeleteEvent();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [deleting, setDeleting] = useState<BusinessEvent | null>(null);
  const [sharing, setSharing] = useState<{ event: BusinessEvent; justCreated: boolean } | null>(null);
  const { data: business } = useBusiness();
  const { data: integrations } = useIntegrations();
  const pageConnected = integrations?.meta.connected ?? false;

  // Events are for restaurants and bars; the menu hides them from everyone else.
  if (business && !offersEvents(business.businessType)) {
    return <Navigate to="/admin" replace />;
  }

  const submit = async () => {
    if (!draft) {
      return;
    }
    const { id, imageUrl: _preview, ...input } = draft;
    try {
      const saved = await save.mutateAsync({
        id,
        input: {
          ...input,
          title: input.title.trim(),
          startTime: input.startTime.slice(0, 5),
          endTime: input.endTime ? input.endTime.slice(0, 5) : null,
        },
      });
      toast.success(id ? 'Event saved' : 'Event created');
      setDraft(null);
      // A new event is the moment to announce it.
      if (!id && pageConnected) {
        setSharing({ event: saved, justCreated: true });
      }
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  const edit = (event: BusinessEvent) => {
    const { state: _state, sharedOn: _sharedOn, ...editable } = event;
    setDraft(editable);
  };

  return (
    <>
      <PageHeader
        title="Events"
        description="Live music, quiz nights, tastings: shown on your booking page before they happen."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setDraft(EMPTY)}>
            New event
          </Button>
        }
      />
      {isError ? <ErrorState error={error} onRetry={() => refetch()} /> : null}
      {isLoading ? (
        <Paper variant="outlined">
          <TableLoadingRows />
        </Paper>
      ) : events?.length === 0 ? (
        <EmptyState
          icon={<CelebrationRoundedIcon />}
          title="No events yet"
          description="Add a band night or a tasting with a picture; customers see it on your booking page."
        />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Event</TableCell>
                <TableCell>When</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>On the page</TableCell>
                <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>Booked as</TableCell>
                <TableCell>State</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {events?.map((event) => (
                <TableRow key={event.id} hover>
                  <TableCell>
                    <Stack direction="row" spacing={1.5} alignItems="center">
                      <Avatar variant="rounded" src={mediaUrl(event.imageUrl)} sx={{ width: 44, height: 44, bgcolor: 'primary.main' }}>
                        <CelebrationRoundedIcon fontSize="small" />
                      </Avatar>
                      <div>
                        <Typography variant="body2" fontWeight={700}>
                          {event.title}
                        </Typography>
                        {event.performer ? (
                          <Typography variant="caption" color="text.secondary">
                            {event.performer}
                          </Typography>
                        ) : null}
                      </div>
                    </Stack>
                  </TableCell>
                  <TableCell>
                    {formatLocalDate(event.date)}
                    <Typography variant="caption" color="text.secondary" display="block">
                      {formatEventHours(event)}
                    </Typography>
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                    {formatShortDate(event.showFrom)} – {formatShortDate(event.showUntil)}
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>
                    {event.serviceId ? (services?.find((s) => s.id === event.serviceId)?.name ?? '—') : 'Announcement only'}
                  </TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={0.5} alignItems="center">
                      <Chip size="small" variant="outlined" color={STATE_COLORS[event.state]} label={EVENT_STATE_LABELS[event.state]} />
                      {event.sharedOn.includes('FACEBOOK') ? (
                        <Tooltip title="Posted on Facebook">
                          <FacebookRoundedIcon fontSize="small" sx={{ color: '#1877F2' }} />
                        </Tooltip>
                      ) : null}
                      {event.sharedOn.includes('INSTAGRAM') ? (
                        <Tooltip title="Posted on Instagram">
                          <InstagramIcon fontSize="small" sx={{ color: '#C13584' }} />
                        </Tooltip>
                      ) : null}
                    </Stack>
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title="Share on Facebook / Instagram">
                      <IconButton onClick={() => setSharing({ event, justCreated: false })}>
                        <ShareRoundedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Edit">
                      <IconButton onClick={() => edit(event)}>
                        <EditRoundedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton onClick={() => setDeleting(event)}>
                        <DeleteOutlineRoundedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={draft !== null} onClose={() => setDraft(null)} maxWidth="sm" fullWidth>
        <DialogTitle>{draft?.id ? 'Edit event' : 'New event'}</DialogTitle>
        {draft ? (
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 0 }}>
              <Grid item xs={12}>
                <ImageUpload
                  label="Picture"
                  hint="A poster or a photo of the band. Shown on your booking page."
                  url={draft.imageUrl}
                  aspectRatio="16 / 9"
                  width="100%"
                  onChange={(media) => setDraft({ ...draft, imageMediaId: media?.id ?? null, imageUrl: media?.url ?? null })}
                />
              </Grid>
              <Grid item xs={12} sm={7}>
                <TextField autoFocus fullWidth label="Title" placeholder="Friday live music" value={draft.title} onChange={(e) => setDraft({ ...draft, title: e.target.value })} />
              </Grid>
              <Grid item xs={12} sm={5}>
                <TextField fullWidth label="Band / performer" placeholder="Optional" value={draft.performer ?? ''} onChange={(e) => setDraft({ ...draft, performer: e.target.value })} />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  fullWidth
                  multiline
                  minRows={3}
                  label="Details"
                  placeholder="What to expect, the menu, the dress code…"
                  value={draft.description ?? ''}
                  onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                />
              </Grid>
              <Grid item xs={12} sm={4}>
                <TextField fullWidth type="date" label="Date" value={draft.date} InputLabelProps={{ shrink: true }} onChange={(e) => setDraft({ ...draft, date: e.target.value })} />
              </Grid>
              <Grid item xs={6} sm={4}>
                <TextField fullWidth type="time" label="Starts" value={draft.startTime.slice(0, 5)} InputLabelProps={{ shrink: true }} onChange={(e) => setDraft({ ...draft, startTime: e.target.value })} />
              </Grid>
              <Grid item xs={6} sm={4}>
                <TextField
                  fullWidth
                  type="time"
                  label="Ends"
                  value={draft.endTime?.slice(0, 5) ?? ''}
                  InputLabelProps={{ shrink: true }}
                  helperText="Optional"
                  onChange={(e) => setDraft({ ...draft, endTime: e.target.value || null })}
                />
              </Grid>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  type="date"
                  label="Show from"
                  value={draft.showFrom ?? ''}
                  InputLabelProps={{ shrink: true }}
                  helperText="Empty = a week before"
                  onChange={(e) => setDraft({ ...draft, showFrom: e.target.value || null })}
                />
              </Grid>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  type="date"
                  label="Show until"
                  value={draft.showUntil ?? ''}
                  InputLabelProps={{ shrink: true }}
                  helperText="Empty = the day of the event"
                  onChange={(e) => setDraft({ ...draft, showUntil: e.target.value || null })}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  select
                  fullWidth
                  label="Booked as"
                  value={draft.serviceId ?? ''}
                  onChange={(e) => setDraft({ ...draft, serviceId: e.target.value === '' ? null : Number(e.target.value) })}
                  helperText="With a service, customers can reserve for the event and see when it is fully booked."
                >
                  <MenuItem value="">Announcement only</MenuItem>
                  {(services ?? [])
                    .filter((service) => service.active || service.id === draft.serviceId)
                    .map((service) => (
                      <MenuItem key={service.id} value={service.id}>
                        {service.name}
                      </MenuItem>
                    ))}
                </TextField>
              </Grid>
            </Grid>
          </DialogContent>
        ) : null}
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDraft(null)}>Cancel</Button>
          <Button variant="contained" onClick={submit} disabled={!draft?.title.trim() || !draft?.date || !draft?.startTime || save.isPending}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <ShareEventDialog
        event={sharing?.event ?? null}
        intro={sharing?.justCreated ? 'Your event is on your booking page. Announce it on Facebook and Instagram too?' : undefined}
        onClose={() => setSharing(null)}
      />

      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.title ?? 'event'}?`}
        message="It disappears from your booking page. Reservations already made are kept."
        confirmLabel="Delete"
        busy={remove.isPending}
        onConfirm={() =>
          deleting &&
          remove
            .mutateAsync(deleting.id)
            .then(() => toast.success('Event deleted'))
            .catch((err) => toast.error(errorMessage(err)))
            .finally(() => setDeleting(null))
        }
        onClose={() => setDeleting(null)}
      />
    </>
  );
}
