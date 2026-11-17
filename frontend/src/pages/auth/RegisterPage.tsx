import { useState } from 'react';
import { Link as RouterLink, Navigate, useNavigate } from 'react-router-dom';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useAuth } from '@/features/auth/AuthContext';
import { errorMessage } from '@/api/client';
import { AuthShell } from '@/pages/auth/LoginPage';
import { PasswordField } from '@/components/PasswordField';
import { VerificationCodeForm } from '@/features/auth/VerificationCodeForm';
import { clearPendingSignup, loadPendingSignup, savePendingSignup, type PendingSignup } from '@/features/auth/pendingSignup';
import { CURRENCIES, currencyForTimezone } from '@/features/business/currencies';
import { BusinessTypePicker } from '@/features/onboarding/BusinessTypePicker';
import { BUSINESS_TYPES } from '@/features/onboarding/businessTypes';
import type { BusinessType } from '@/types/api';

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

const DEFAULT_TIMEZONE = 'Europe/Skopje';

/** Fields two by two on larger screens, one under the other on phones. */
const PAIRS = {
  display: 'grid',
  gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' },
  columnGap: 2,
  rowGap: 2.5,
  alignItems: 'start',
} as const;

const schema = z.object({
  fullName: z.string().min(1, 'Your name is required').max(160),
  email: z.string().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(8, 'Use at least 8 characters').max(128),
  businessName: z.string().min(1, 'Business name is required').max(160),
  timezone: z.string().min(1, 'Pick a timezone'),
  businessType: z.enum(BUSINESS_TYPES.map((info) => info.type) as [BusinessType, ...BusinessType[]], {
    errorMap: () => ({ message: 'Pick what kind of business it is' }),
  }),
  currency: z.string().length(3),
});

type FormValues = z.infer<typeof schema>;

/**
 * Business sign-up in two steps: the details, then the code emailed to the
 * owner. Nothing is created until the code proves the address is theirs.
 */
export function RegisterPage() {
  const { register: signUp, verifyRegistration, resendSignupCode, user, initialising } = useAuth();
  const navigate = useNavigate();
  const [submitError, setSubmitError] = useState<string | null>(null);
  /** The sign-up waiting for its code (kept across a reload); null while filling in the form. */
  const [pending, setPending] = useState<PendingSignup | null>(() => loadPendingSignup('owner'));

  // Skopje and denars by default; the browser's own zone is still offered.
  const detectedZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const timezones = COMMON_TIMEZONES.includes(detectedZone)
    ? COMMON_TIMEZONES
    : [detectedZone, ...COMMON_TIMEZONES];

  const defaultCurrency = currencyForTimezone(DEFAULT_TIMEZONE);

  const {
    register,
    control,
    handleSubmit,
    setValue,
    getFieldState,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      fullName: '',
      email: '',
      password: '',
      businessName: '',
      timezone: DEFAULT_TIMEZONE,
      businessType: undefined,
      currency: defaultCurrency,
    },
  });
  const timezoneField = register('timezone');

  // Someone signed in only for booking may still sign up their business here.
  if (!initialising && user?.activeBusiness) {
    return <Navigate to="/admin" replace />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);
    const details = { ...values, email: values.email.trim() };
    try {
      const { existingAccount } = await signUp(details);
      const waiting = { email: details.email, businessName: details.businessName, existingAccount };
      savePendingSignup('owner', waiting);
      setPending(waiting);
    } catch (error) {
      setSubmitError(errorMessage(error, 'Could not create the account.'));
    }
  });

  if (pending) {
    return (
      <AuthShell title="Confirm your email" subtitle="One last step before your dashboard.">
        {pending.existingAccount ? (
          <Alert severity="info" sx={{ mb: 2.5 }}>
            You already have an account on this platform with this email, for example from booking somewhere. When
            you confirm, {pending.businessName} is added to that account and your password becomes the one you just
            entered.
          </Alert>
        ) : null}
        <VerificationCodeForm
          email={pending.email}
          purpose="create your business account"
          onVerify={async (code) => {
            await verifyRegistration(pending.email, code);
            clearPendingSignup('owner');
            navigate('/admin', { replace: true });
          }}
          onResend={() => resendSignupCode(pending.email)}
          onBack={() => {
            clearPendingSignup('owner');
            setPending(null);
          }}
        />
      </AuthShell>
    );
  }

  return (
    <AuthShell
      wide
      title="Create your business"
      subtitle="One account, one booking page. You can invite staff and change everything later."
    >
      <form onSubmit={onSubmit} noValidate>
        <Stack spacing={2.5}>
          {submitError ? <Alert severity="error">{submitError}</Alert> : null}
          <Controller
            name="businessType"
            control={control}
            render={({ field, fieldState }) => (
              <BusinessTypePicker value={field.value} onChange={field.onChange} error={fieldState.error?.message} />
            )}
          />
          <Box sx={PAIRS}>
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
              helperText={errors.email?.message ?? 'We will send a code to confirm it.'}
              {...register('email')}
            />
            <PasswordField
              label="Password"
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
              defaultValue={DEFAULT_TIMEZONE}
              error={Boolean(errors.timezone)}
              helperText={errors.timezone?.message ?? 'All booking times are shown in this zone'}
              {...timezoneField}
              onChange={(event) => {
                void timezoneField.onChange(event);
                // The currency follows the zone until the owner picks one themselves.
                if (!getFieldState('currency').isDirty) {
                  setValue('currency', currencyForTimezone(event.target.value));
                }
              }}
            >
              {timezones.map((zone) => (
                <MenuItem key={zone} value={zone}>
                  {zone}
                </MenuItem>
              ))}
            </TextField>
            <Controller
              name="currency"
              control={control}
              render={({ field }) => (
                <TextField
                  select
                  label="Currency"
                  fullWidth
                  value={field.value}
                  onChange={field.onChange}
                  helperText="Your prices are in this currency"
                >
                  {CURRENCIES.map((code) => (
                    <MenuItem key={code} value={code}>
                      {code}
                    </MenuItem>
                  ))}
                </TextField>
              )}
            />
          </Box>
          <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
            {isSubmitting ? 'Sending code…' : 'Create account'}
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
