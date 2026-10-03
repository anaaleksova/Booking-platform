import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import { errorMessage } from '@/api/client';
import { PageHeader } from '@/components/PageHeader';
import { ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { WeeklyHoursEditor } from '@/components/WeeklyHoursEditor';
import {
  useBlockedTimes,
  useBusiness,
  useBusinessSettings,
  useCreateBlockedTime,
  useDeleteBlockedTime,
  useOpeningHours,
  useReplaceOpeningHours,
  useUpdateBusiness,
  useUpdateBusinessSettings,
  toBusinessInput,
  type BusinessInput,
} from '@/features/business/api';
import { BookingPageTab } from '@/features/business/BookingPageTab';
import { useResources } from '@/features/catalog/api';
import type { BusinessSettings, DayInterval } from '@/types/api';
import { formatLocalDate, formatLocalTime } from '@/utils/format';

const CURRENCIES = ['EUR', 'USD', 'GBP', 'CHF', 'MKD', 'RSD', 'BGN', 'SEK', 'NOK', 'DKK', 'PLN', 'CZK', 'HUF', 'RON'];

export function SettingsPage() {
  const [tab, setTab] = useState(0);
  return (
    <>
      <PageHeader title="Settings" description="Your business profile, booking page, opening hours and booking rules." />
      <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" sx={{ mb: 3 }}>
        <Tab label="Business" />
        <Tab label="Booking page" />
        <Tab label="Opening hours" />
        <Tab label="Booking rules" />
        <Tab label="Closures & time off" />
      </Tabs>
      {tab === 0 ? <BusinessTab /> : null}
      {tab === 1 ? <BookingPageTab /> : null}
      {tab === 2 ? <OpeningHoursTab /> : null}
      {tab === 3 ? <RulesTab /> : null}
      {tab === 4 ? <BlockedTimeTab /> : null}
    </>
  );
}

function BusinessTab() {
  const toast = useToast();
  const { data, isLoading, isError, error } = useBusiness();
  const update = useUpdateBusiness();
  const [form, setForm] = useState<BusinessInput | null>(null);

  useEffect(() => {
    if (data) {
      setForm(toBusinessInput(data));
    }
  }, [data]);

  if (isLoading || !form) {
    return isError ? <ErrorState error={error} /> : <LoadingState />;
  }
  const field = (key: keyof BusinessInput, label: string, extra: object = {}) => (
    <TextField fullWidth label={label} value={form[key] ?? ''} onChange={(e) => setForm({ ...form, [key]: e.target.value })} {...extra} />
  );
  const zones = (Intl as unknown as { supportedValuesOf?: (key: string) => string[] }).supportedValuesOf?.('timeZone') ?? [form.timezone];

  return (
    <Paper variant="outlined" sx={{ p: 3, maxWidth: 820 }}>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6}>{field('name', 'Business name')}</Grid>
        <Grid item xs={12} sm={6}>
          {field('slug', 'Booking page address', {
            InputProps: { startAdornment: <InputAdornment position="start">/b/</InputAdornment> },
            helperText: 'Lower-case letters, digits and dashes',
          })}
        </Grid>
        <Grid item xs={12} sm={6}>{field('email', 'Email')}</Grid>
        <Grid item xs={12} sm={6}>{field('phone', 'Phone')}</Grid>
        <Grid item xs={12}>{field('address', 'Address')}</Grid>
        <Grid item xs={6} sm={3}>
          <TextField select fullWidth label="Currency" value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })}>
            {Array.from(new Set([form.currency, ...CURRENCIES])).map((code) => (
              <MenuItem key={code} value={code}>{code}</MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid item xs={6} sm={3}>
          <TextField select fullWidth label="Timezone" value={form.timezone} onChange={(e) => setForm({ ...form, timezone: e.target.value })} SelectProps={{ MenuProps: { PaperProps: { sx: { maxHeight: 360 } } } }}>
            {zones.map((zone) => (
              <MenuItem key={zone} value={zone}>{zone}</MenuItem>
            ))}
          </TextField>
        </Grid>
      </Grid>
      <Alert severity="info" sx={{ mt: 2 }}>
        Changing the currency does not convert existing prices — each reservation keeps the currency it was booked in.
      </Alert>
      <Button
        variant="contained"
        sx={{ mt: 2 }}
        disabled={update.isPending}
        onClick={() =>
          update
            .mutateAsync(form)
            .then(() => toast.success('Business details saved'))
            .catch((err) => toast.error(errorMessage(err)))
        }
      >
        Save
      </Button>
    </Paper>
  );
}

function OpeningHoursTab() {
  const toast = useToast();
  const { data, isLoading } = useOpeningHours();
  const replace = useReplaceOpeningHours();
  const [hours, setHours] = useState<DayInterval[] | null>(null);

  useEffect(() => {
    if (data) {
      setHours(data);
    }
  }, [data]);

  if (isLoading || !hours) {
    return <LoadingState />;
  }
  return (
    <Stack spacing={2} sx={{ maxWidth: 760 }}>
      <Typography variant="body2" color="text.secondary">
        No resource can be reserved outside these hours. Add a second interval to a day for a break.
      </Typography>
      <WeeklyHoursEditor value={hours} onChange={setHours} />
      <Button
        variant="contained"
        sx={{ alignSelf: 'flex-start' }}
        disabled={replace.isPending}
        onClick={() =>
          replace
            .mutateAsync(hours)
            .then(() => toast.success('Opening hours saved'))
            .catch((err) => toast.error(errorMessage(err)))
        }
      >
        Save opening hours
      </Button>
    </Stack>
  );
}

function RulesTab() {
  const toast = useToast();
  const { data, isLoading } = useBusinessSettings();
  const update = useUpdateBusinessSettings();
  const [form, setForm] = useState<BusinessSettings | null>(null);

  useEffect(() => {
    if (data) {
      setForm(data);
    }
  }, [data]);

  if (isLoading || !form) {
    return <LoadingState />;
  }
  const number = (key: keyof BusinessSettings, label: string, unit: string, helperText?: string) => (
    <TextField
      fullWidth
      type="number"
      label={label}
      value={form[key] as number}
      onChange={(e) => setForm({ ...form, [key]: Number(e.target.value) })}
      InputProps={{ endAdornment: <InputAdornment position="end">{unit}</InputAdornment> }}
      helperText={helperText}
    />
  );
  const toggle = (key: keyof BusinessSettings, label: string) => (
    <FormControlLabel control={<Switch checked={form[key] as boolean} onChange={(e) => setForm({ ...form, [key]: e.target.checked })} />} label={label} />
  );

  return (
    <Paper variant="outlined" sx={{ p: 3, maxWidth: 820 }}>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={4}>{number('slotIntervalMinutes', 'Time slot every', 'min', 'Spacing of offered start times')}</Grid>
        <Grid item xs={12} sm={4}>{number('minimumBookingNoticeMinutes', 'Minimum notice', 'min', 'For online bookings')}</Grid>
        <Grid item xs={12} sm={4}>{number('maximumBookingAdvanceDays', 'Book up to', 'days ahead')}</Grid>
        <Grid item xs={12} sm={6}>{toggle('allowCustomerCancellation', 'Customers may cancel online')}</Grid>
        <Grid item xs={12} sm={6}>{number('cancellationDeadlineHours', 'Cancellation deadline', 'hours before')}</Grid>
        <Grid item xs={12} sm={6}>{toggle('emailNotificationsEnabled', 'Email confirmations')}</Grid>
        <Grid item xs={12} sm={6}>{toggle('reminderNotificationsEnabled', 'Email reminders')}</Grid>
        <Grid item xs={12} sm={6}>{number('reminderLeadHours', 'Send reminders', 'hours before')}</Grid>
      </Grid>
      <Button
        variant="contained"
        sx={{ mt: 2 }}
        disabled={update.isPending}
        onClick={() =>
          update
            .mutateAsync(form)
            .then(() => toast.success('Booking rules saved'))
            .catch((err) => toast.error(errorMessage(err)))
        }
      >
        Save
      </Button>
    </Paper>
  );
}

function BlockedTimeTab() {
  const toast = useToast();
  const { data: blocks, isLoading } = useBlockedTimes();
  const { data: resources } = useResources();
  const create = useCreateBlockedTime();
  const remove = useDeleteBlockedTime();
  const [draft, setDraft] = useState({ resourceId: '' as number | '', start: '', end: '', reason: '' });

  return (
    <Grid container spacing={3}>
      <Grid item xs={12} md={5}>
        <Paper variant="outlined" sx={{ p: 3 }}>
          <Typography variant="subtitle1" fontWeight={700} gutterBottom>
            Block time
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Close the whole business (a holiday) or take one resource out — an employee's day off, a room in maintenance.
          </Typography>
          <Stack spacing={2}>
            <TextField select label="Applies to" value={draft.resourceId} onChange={(e) => setDraft({ ...draft, resourceId: e.target.value === '' ? '' : Number(e.target.value) })}>
              <MenuItem value="">The whole business</MenuItem>
              {(resources ?? []).map((resource) => (
                <MenuItem key={resource.id} value={resource.id}>
                  {resource.name} · {resource.resourceTypeName}
                </MenuItem>
              ))}
            </TextField>
            <TextField type="datetime-local" label="From" value={draft.start} InputLabelProps={{ shrink: true }} onChange={(e) => setDraft({ ...draft, start: e.target.value })} />
            <TextField type="datetime-local" label="To" value={draft.end} InputLabelProps={{ shrink: true }} onChange={(e) => setDraft({ ...draft, end: e.target.value })} />
            <TextField label="Reason" value={draft.reason} onChange={(e) => setDraft({ ...draft, reason: e.target.value })} />
            <Button
              variant="contained"
              disabled={!draft.start || !draft.end || create.isPending}
              onClick={() =>
                create
                  .mutateAsync({
                    resourceId: draft.resourceId === '' ? null : draft.resourceId,
                    startTime: draft.start.length === 16 ? `${draft.start}:00` : draft.start,
                    endTime: draft.end.length === 16 ? `${draft.end}:00` : draft.end,
                    reason: draft.reason || undefined,
                  })
                  .then(() => {
                    toast.success('Time blocked');
                    setDraft({ resourceId: '', start: '', end: '', reason: '' });
                  })
                  .catch((err) => toast.error(errorMessage(err)))
              }
            >
              Block time
            </Button>
          </Stack>
        </Paper>
      </Grid>
      <Grid item xs={12} md={7}>
        <Paper variant="outlined">
          <Typography variant="subtitle1" fontWeight={700} sx={{ p: 2, pb: 0 }}>
            Upcoming
          </Typography>
          {isLoading ? (
            <LoadingState />
          ) : blocks?.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
              Nothing blocked in the next three months.
            </Typography>
          ) : (
            <List>
              {blocks?.map((block) => (
                <ListItem
                  key={block.id}
                  secondaryAction={
                    <IconButton
                      aria-label="Remove"
                      onClick={() => remove.mutateAsync(block.id).catch((err) => toast.error(errorMessage(err)))}
                    >
                      <DeleteOutlineRoundedIcon fontSize="small" />
                    </IconButton>
                  }
                >
                  <ListItemText
                    primary={`${block.resourceName ?? 'Whole business'}${block.reason ? ` — ${block.reason}` : ''}`}
                    secondary={`${formatLocalDate(block.localStartTime)} ${formatLocalTime(block.localStartTime)} → ${formatLocalDate(block.localEndTime)} ${formatLocalTime(block.localEndTime)}`}
                  />
                </ListItem>
              ))}
            </List>
          )}
        </Paper>
      </Grid>
    </Grid>
  );
}
