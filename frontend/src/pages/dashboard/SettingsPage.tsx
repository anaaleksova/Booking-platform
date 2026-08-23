import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Divider from '@mui/material/Divider';
import FormControlLabel from '@mui/material/FormControlLabel';
import Grid from '@mui/material/Grid';
import InputAdornment from '@mui/material/InputAdornment';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { PageHeader } from '@/components/PageHeader';
import { ErrorState, LoadingState } from '@/components/states';
import { useToast } from '@/components/ToastProvider';
import {
  useBusiness,
  useBusinessSettings,
  useUpdateBusiness,
  useUpdateBusinessSettings,
} from '@/features/businesses/useBusiness';
import { errorMessage } from '@/api/client';
import type { BusinessSettings } from '@/types/api';

export function SettingsPage() {
  const { data: business, isLoading, isError, error, refetch } = useBusiness();
  const { data: settings } = useBusinessSettings();

  return (
    <>
      <PageHeader title="Settings" description="Your public profile and the rules bookings follow." />

      {isError ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}
      {isLoading ? <LoadingState /> : null}

      <Grid container spacing={2.5}>
        <Grid item xs={12} lg={7}>
          {business ? <ProfileCard business={business} /> : null}
        </Grid>
        <Grid item xs={12} lg={5}>
          {settings ? <RulesCard settings={settings} /> : null}
        </Grid>
      </Grid>
    </>
  );
}

function ProfileCard({ business }: { business: NonNullable<ReturnType<typeof useBusiness>['data']> }) {
  const toast = useToast();
  const updateBusiness = useUpdateBusiness();
  const [form, setForm] = useState({
    name: business.name,
    slug: business.slug,
    description: business.description ?? '',
    logoUrl: business.logoUrl ?? '',
    phone: business.phone ?? '',
    email: business.email ?? '',
    address: business.address ?? '',
    timezone: business.timezone,
  });
  const [submitError, setSubmitError] = useState<string | null>(null);

  const save = async () => {
    setSubmitError(null);
    try {
      await updateBusiness.mutateAsync({
        name: form.name.trim(),
        slug: form.slug.trim(),
        description: form.description.trim() || undefined,
        logoUrl: form.logoUrl.trim() || undefined,
        phone: form.phone.trim() || undefined,
        email: form.email.trim() || undefined,
        address: form.address.trim() || undefined,
        timezone: form.timezone.trim(),
      });
      toast.success('Business profile saved.');
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  };

  return (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Business profile
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          This is what customers see on your public booking page.
        </Typography>

        <Stack spacing={2.5}>
          <TextField
            label="Business name"
            fullWidth
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
          <TextField
            label="Booking page address"
            fullWidth
            value={form.slug}
            onChange={(event) => setForm({ ...form, slug: event.target.value })}
            InputProps={{ startAdornment: <InputAdornment position="start">/book/</InputAdornment> }}
            helperText="Lower-case letters, digits and dashes. Changing this breaks existing links."
          />
          <TextField
            label="Description"
            fullWidth
            multiline
            minRows={3}
            value={form.description}
            onChange={(event) => setForm({ ...form, description: event.target.value })}
          />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              label="Phone"
              fullWidth
              value={form.phone}
              onChange={(event) => setForm({ ...form, phone: event.target.value })}
            />
            <TextField
              label="Email"
              type="email"
              fullWidth
              value={form.email}
              onChange={(event) => setForm({ ...form, email: event.target.value })}
              helperText="Where new booking notifications are sent"
            />
          </Stack>
          <TextField
            label="Address"
            fullWidth
            value={form.address}
            onChange={(event) => setForm({ ...form, address: event.target.value })}
          />
          <TextField
            label="Logo URL"
            fullWidth
            value={form.logoUrl}
            onChange={(event) => setForm({ ...form, logoUrl: event.target.value })}
          />
          <TextField
            label="Timezone"
            fullWidth
            value={form.timezone}
            onChange={(event) => setForm({ ...form, timezone: event.target.value })}
            helperText="IANA zone, e.g. Europe/Skopje. Every booking time is interpreted in this zone."
          />

          {submitError ? <Alert severity="error">{submitError}</Alert> : null}

          <Button
            variant="contained"
            onClick={() => void save()}
            disabled={updateBusiness.isPending}
            sx={{ alignSelf: 'flex-start' }}
          >
            {updateBusiness.isPending ? 'Saving…' : 'Save profile'}
          </Button>
        </Stack>
      </CardContent>
    </Card>
  );
}

function RulesCard({ settings }: { settings: BusinessSettings }) {
  const toast = useToast();
  const updateSettings = useUpdateBusinessSettings();
  const [form, setForm] = useState<BusinessSettings>(settings);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => setForm(settings), [settings]);

  const save = async () => {
    setSubmitError(null);
    try {
      await updateSettings.mutateAsync(form);
      toast.success('Booking rules saved.');
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  };

  const number = (key: keyof BusinessSettings, label: string, helper?: string, adornment = 'minutes') => (
    <TextField
      label={label}
      type="number"
      fullWidth
      value={form[key] ?? ''}
      onChange={(event) => setForm({ ...form, [key]: Number(event.target.value) })}
      helperText={helper}
      InputProps={{ endAdornment: <InputAdornment position="end">{adornment}</InputAdornment> }}
    />
  );

  return (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Booking rules
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          These apply to customers booking themselves. You can always book any free slot from the dashboard.
        </Typography>

        <Stack spacing={2.5}>
          {number('minimumBookingNoticeMinutes', 'Minimum notice', 'How soon before an appointment customers may still book')}
          {number('maximumBookingAdvanceDays', 'Maximum advance', 'How far ahead the booking page goes', 'days')}
          {number('slotIntervalMinutes', 'Slot interval', 'The grid start times fall on: 15, 30, 60…')}

          <Divider />

          <FormControlLabel
            control={
              <Switch
                checked={form.allowCustomerCancellation}
                onChange={(event) => setForm({ ...form, allowCustomerCancellation: event.target.checked })}
              />
            }
            label="Customers may cancel their own bookings"
          />
          {form.allowCustomerCancellation
            ? number('cancellationDeadlineHours', 'Cancellation deadline', 'Cancelling is closed this long before the appointment', 'hours')
            : null}

          <Divider />

          <FormControlLabel
            control={
              <Switch
                checked={form.emailNotificationsEnabled}
                onChange={(event) => setForm({ ...form, emailNotificationsEnabled: event.target.checked })}
              />
            }
            label="Send email notifications"
          />
          <FormControlLabel
            control={
              <Switch
                checked={form.reminderNotificationsEnabled}
                onChange={(event) => setForm({ ...form, reminderNotificationsEnabled: event.target.checked })}
              />
            }
            label="Send appointment reminders"
          />
          {form.reminderNotificationsEnabled
            ? number('reminderLeadHours', 'Reminder lead time', 'How long before the appointment the reminder is sent', 'hours')
            : null}

          {submitError ? <Alert severity="error">{submitError}</Alert> : null}

          <Button
            variant="contained"
            onClick={() => void save()}
            disabled={updateSettings.isPending}
            sx={{ alignSelf: 'flex-start' }}
          >
            {updateSettings.isPending ? 'Saving…' : 'Save rules'}
          </Button>
        </Stack>
      </CardContent>
    </Card>
  );
}
