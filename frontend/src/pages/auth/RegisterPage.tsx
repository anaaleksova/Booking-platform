import { useState } from 'react';
import { Link as RouterLink, Navigate, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useAuth } from '@/features/auth/AuthContext';
import { errorMessage } from '@/api/client';
import { AuthShell } from '@/pages/auth/LoginPage';

/** A short list plus the browser's own zone, which covers most sign-ups. */
const COMMON_TIMEZONES = [
  'Europe/Skopje',
  'Europe/Belgrade',
  'Europe/Berlin',
  'Europe/London',
  'Europe/Istanbul',
  'America/New_York',
  'America/Chicago',
  'UTC',
];

const schema = z.object({
  fullName: z.string().min(1, 'Your name is required').max(160),
  email: z.string().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(8, 'Use at least 8 characters').max(128),
  businessName: z.string().min(1, 'Business name is required').max(160),
  timezone: z.string().min(1, 'Pick a timezone'),
});

type FormValues = z.infer<typeof schema>;

export function RegisterPage() {
  const { register: signUp, isAuthenticated, initialising } = useAuth();
  const navigate = useNavigate();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const detectedZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const timezones = COMMON_TIMEZONES.includes(detectedZone)
    ? COMMON_TIMEZONES
    : [detectedZone, ...COMMON_TIMEZONES];

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', email: '', password: '', businessName: '', timezone: detectedZone },
  });

  if (!initialising && isAuthenticated) {
    return <Navigate to="/admin" replace />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await signUp(values);
      navigate('/admin', { replace: true });
    } catch (error) {
      setSubmitError(errorMessage(error, 'Could not create the account.'));
    }
  });

  return (
    <AuthShell
      title="Create your business"
      subtitle="One account, one booking page. You can invite staff and change everything later."
    >
      <form onSubmit={onSubmit} noValidate>
        <Stack spacing={2.5}>
          {submitError ? <Alert severity="error">{submitError}</Alert> : null}
          <TextField
            label="Your name"
            autoFocus
            fullWidth
            error={Boolean(errors.fullName)}
            helperText={errors.fullName?.message}
            {...register('fullName')}
          />
          <TextField
            label="Business name"
            fullWidth
            error={Boolean(errors.businessName)}
            helperText={errors.businessName?.message ?? 'Used for your public booking page address'}
            {...register('businessName')}
          />
          <TextField
            label="Email"
            type="email"
            autoComplete="email"
            fullWidth
            error={Boolean(errors.email)}
            helperText={errors.email?.message}
            {...register('email')}
          />
          <TextField
            label="Password"
            type="password"
            autoComplete="new-password"
            fullWidth
            error={Boolean(errors.password)}
            helperText={errors.password?.message ?? 'At least 8 characters'}
            {...register('password')}
          />
          <TextField
            select
            label="Timezone"
            fullWidth
            defaultValue={detectedZone}
            error={Boolean(errors.timezone)}
            helperText={errors.timezone?.message ?? 'All booking times are shown in this zone'}
            {...register('timezone')}
          >
            {timezones.map((zone) => (
              <MenuItem key={zone} value={zone}>
                {zone}
              </MenuItem>
            ))}
          </TextField>
          <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
            {isSubmitting ? 'Creating…' : 'Create account'}
          </Button>
          <Typography variant="body2" color="text.secondary" textAlign="center">
            Already have an account?{' '}
            <Link component={RouterLink} to="/login">
              Sign in
            </Link>
          </Typography>
        </Stack>
      </form>
    </AuthShell>
  );
}
