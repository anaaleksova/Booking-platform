import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import EventBusyRoundedIcon from '@mui/icons-material/EventBusyRounded';
import dayjs from 'dayjs';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { WeeklyHoursEditor } from '@/features/availability/WeeklyHoursEditor';
import {
  useBlockedTimes,
  useCreateBlockedTime,
  useDeleteBlockedTime,
} from '@/features/availability/useAvailability';
import { useReplaceWorkingHours, useWorkingHours } from '@/features/businesses/useBusiness';
import { useResources } from '@/features/resources/useResources';
import { useStaff } from '@/features/staff/useStaff';
import { errorMessage } from '@/api/client';
import { formatLocalDate, formatLocalTime } from '@/utils/format';
import type { DayInterval } from '@/types/api';

/**
 * Two things decide when the business can be booked: the recurring week, and
 * the one-off exceptions. Both are edited here.
 */
export function AvailabilityPage() {
  const toast = useToast();
  const { data: hours, isLoading, isError, error, refetch } = useWorkingHours();
  const replaceHours = useReplaceWorkingHours();
  const [draft, setDraft] = useState<DayInterval[]>([]);
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    if (hours) {
      setDraft(hours);
      setDirty(false);
    }
  }, [hours]);

  const save = async () => {
    try {
      await replaceHours.mutateAsync(draft);
      toast.success('Working hours saved.');
      setDirty(false);
    } catch (saveError) {
      toast.error(errorMessage(saveError));
    }
  };

  return (
    <>
      <PageHeader
        title="Availability"
        description="Opening hours set the outer bound of everything bookable. Blocked time punches holes in it."
      />

      {isError ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

      <Grid container spacing={2.5}>
        <Grid item xs={12} lg={7}>
          <Card>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
                <Typography variant="h6">Opening hours</Typography>
                <Button
                  variant="contained"
                  disabled={!dirty || replaceHours.isPending}
                  onClick={() => void save()}
                >
                  {replaceHours.isPending ? 'Saving…' : 'Save changes'}
                </Button>
              </Stack>
              {isLoading ? (
                <LoadingState />
              ) : (
                <WeeklyHoursEditor
                  value={draft}
                  onChange={(intervals) => {
                    setDraft(intervals);
                    setDirty(true);
                  }}
                />
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={5}>
          <BlockedTimesCard />
        </Grid>
      </Grid>
    </>
  );
}

function BlockedTimesCard() {
  const toast = useToast();
  const from = dayjs().format('YYYY-MM-DD');
  const to = dayjs().add(3, 'month').format('YYYY-MM-DD');
  const { data: blocks = [], isLoading } = useBlockedTimes(from, to);
  const deleteBlock = useDeleteBlockedTime();
  const [dialogOpen, setDialogOpen] = useState(false);

  return (
    <>
      <Card>
        <CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
            <Typography variant="h6">Blocked time</Typography>
            <Button size="small" startIcon={<AddRoundedIcon />} onClick={() => setDialogOpen(true)}>
              Block time
            </Button>
          </Stack>

          {isLoading ? (
            <LoadingState />
          ) : blocks.length === 0 ? (
            <EmptyState
              title="Nothing blocked"
              description="Holidays, meetings, maintenance or personal time — block it here and it disappears from availability."
              icon={<EventBusyRoundedIcon />}
            />
          ) : (
            <List disablePadding>
              {blocks.map((block) => (
                <ListItem
                  key={block.id}
                  divider
                  secondaryAction={
                    <IconButton
                      edge="end"
                      size="small"
                      onClick={() => {
                        deleteBlock.mutate(block.id, {
                          onSuccess: () => toast.success('Block removed.'),
                          onError: (deleteError) => toast.error(errorMessage(deleteError)),
                        });
                      }}
                    >
                      <DeleteOutlineRoundedIcon fontSize="small" />
                    </IconButton>
                  }
                >
                  <ListItemText
                    primary={
                      <Typography variant="body2" fontWeight={600}>
                        {formatLocalDate(block.localStartDateTime)} ·{' '}
                        {formatLocalTime(block.localStartDateTime)}–{formatLocalTime(block.localEndDateTime)}
                      </Typography>
                    }
                    secondary={
                      [block.reason, block.staffName, block.resourceName].filter(Boolean).join(' · ') ||
                      'Whole business'
                    }
                  />
                </ListItem>
              ))}
            </List>
          )}
        </CardContent>
      </Card>

      <BlockTimeDialog open={dialogOpen} onClose={() => setDialogOpen(false)} />
    </>
  );
}

function BlockTimeDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const toast = useToast();
  const createBlock = useCreateBlockedTime();
  const { data: staff = [] } = useStaff(true);
  const { data: resources = [] } = useResources(true);

  const [form, setForm] = useState({
    scope: 'business' as 'business' | 'staff' | 'resource',
    staffId: '' as number | '',
    resourceId: '' as number | '',
    date: dayjs().format('YYYY-MM-DD'),
    startTime: '09:00',
    endTime: '17:00',
    reason: '',
  });
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setSubmitError(null);
    }
  }, [open]);

  const submit = async () => {
    setSubmitError(null);
    try {
      await createBlock.mutateAsync({
        staffId: form.scope === 'staff' && form.staffId !== '' ? form.staffId : null,
        resourceId: form.scope === 'resource' && form.resourceId !== '' ? form.resourceId : null,
        // Wall-clock in the business timezone, as typed.
        startDateTime: `${form.date}T${form.startTime}:00`,
        endDateTime: `${form.date}T${form.endTime}:00`,
        reason: form.reason.trim() || undefined,
      });
      toast.success('Time blocked.');
      onClose();
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Block time</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2.5}>
          <TextField
            select
            label="Applies to"
            value={form.scope}
            onChange={(event) => setForm({ ...form, scope: event.target.value as typeof form.scope })}
            fullWidth
          >
            <MenuItem value="business">The whole business</MenuItem>
            <MenuItem value="staff">One staff member</MenuItem>
            <MenuItem value="resource">One resource</MenuItem>
          </TextField>

          {form.scope === 'staff' ? (
            <TextField
              select
              label="Staff member"
              value={form.staffId}
              onChange={(event) => setForm({ ...form, staffId: Number(event.target.value) })}
              fullWidth
            >
              {staff.map((member) => (
                <MenuItem key={member.id} value={member.id}>
                  {member.name}
                </MenuItem>
              ))}
            </TextField>
          ) : null}

          {form.scope === 'resource' ? (
            <TextField
              select
              label="Resource"
              value={form.resourceId}
              onChange={(event) => setForm({ ...form, resourceId: Number(event.target.value) })}
              fullWidth
            >
              {resources.map((resource) => (
                <MenuItem key={resource.id} value={resource.id}>
                  {resource.name}
                </MenuItem>
              ))}
            </TextField>
          ) : null}

          <TextField
            label="Date"
            type="date"
            fullWidth
            value={form.date}
            onChange={(event) => setForm({ ...form, date: event.target.value })}
            InputLabelProps={{ shrink: true }}
          />
          <Stack direction="row" spacing={2}>
            <TextField
              label="From"
              type="time"
              fullWidth
              value={form.startTime}
              onChange={(event) => setForm({ ...form, startTime: event.target.value })}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              label="To"
              type="time"
              fullWidth
              value={form.endTime}
              onChange={(event) => setForm({ ...form, endTime: event.target.value })}
              InputLabelProps={{ shrink: true }}
            />
          </Stack>
          <TextField
            label="Reason"
            fullWidth
            placeholder="Vacation, meeting, maintenance…"
            value={form.reason}
            onChange={(event) => setForm({ ...form, reason: event.target.value })}
          />

          {submitError ? <Alert severity="error">{submitError}</Alert> : null}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={() => void submit()}
          disabled={
            createBlock.isPending ||
            (form.scope === 'staff' && form.staffId === '') ||
            (form.scope === 'resource' && form.resourceId === '')
          }
        >
          Block time
        </Button>
      </DialogActions>
    </Dialog>
  );
}
