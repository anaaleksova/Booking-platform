import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import InputAdornment from '@mui/material/InputAdornment';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { errorMessage } from '@/api/client';
import { PageHeader } from '@/components/PageHeader';
import { ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import { WeeklyHoursEditor } from '@/components/WeeklyHoursEditor';
import {
  useBusiness,
  useBusinessSettings,
  useOpeningHours,
  useReplaceOpeningHours,
  useUpdateBusiness,
  useUpdateBusinessSettings,
  toBusinessInput,
  type BusinessInput,
} from '@/features/business/api';
import { BookingPageTab } from '@/features/business/BookingPageTab';
import { CalendarTab } from '@/features/business/CalendarTab';
import { CURRENCIES } from '@/features/business/currencies';
import { SpecialHoursEditor } from '@/features/schedule/SpecialHours';
import { BlockedTimeList, BlockTimeForm } from '@/features/schedule/TimeOff';
import { useResources } from '@/features/catalog/api';
import type { BusinessSettings, DayInterval } from '@/types/api';

/** Tabs that can be opened by link, e.g. from the Getting started checklist: /admin/settings?tab=hours. */
const LINKABLE_TABS: Record<string, number> = { business: 0, 'booking-page': 1, hours: 2, 'time-off': 4 };

export function SettingsPage() {
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState(() => LINKABLE_TABS[searchParams.get('tab') ?? ''] ?? 0);
  return (
    <>
      <PageHeader title="Settings" description="Your business profile, booking page, opening hours and booking rules." />
      <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" sx={{ mb: 3 }}>
        <Tab label="Business" />
        <Tab label="Booking page" />
        <Tab label="Opening hours" />
        <Tab label="Booking rules" />
        <Tab label="Closures & time off" />
        <Tab label="Calendar" />
      </Tabs>
      {tab === 0 ? <BusinessTab /> : null}
      {tab === 1 ? <BookingPageTab /> : null}
      {tab === 2 ? <OpeningHoursTab /> : null}
      {tab === 3 ? <RulesTab /> : null}
      {tab === 4 ? <BlockedTimeTab /> : null}
      {tab === 5 ? <CalendarTab /> : null}
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
      <SpecialHoursEditor />
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
        <Grid item xs={12}>
          <TextField
            fullWidth
            type="email"
            label="Send new reservations to"
            placeholder="Your login email"
            value={form.notificationEmail ?? ''}
            onChange={(e) => setForm({ ...form, notificationEmail: e.target.value })}
            disabled={!form.emailNotificationsEnabled}
            helperText="Every new, moved or cancelled reservation — from the website, Instagram or the admin panel — is emailed here as a calendar invitation, so it also lands in your calendar. Leave empty to use your login email."
          />
        </Grid>
      </Grid>
      <Button
        variant="contained"
        sx={{ mt: 2 }}
        disabled={update.isPending}
        onClick={() =>
          update
            .mutateAsync({ ...form, notificationEmail: form.notificationEmail?.trim() || null })
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
  const { data: resources } = useResources();
  return (
    <Grid container spacing={3}>
      <Grid item xs={12} md={5}>
        <BlockTimeForm ownerView resources={resources} />
      </Grid>
      <Grid item xs={12} md={7}>
        <BlockedTimeList ownerView />
      </Grid>
    </Grid>
  );
}
