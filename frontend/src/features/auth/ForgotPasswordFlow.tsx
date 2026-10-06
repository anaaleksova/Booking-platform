import { useEffect, useState, type FormEvent } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import KeyRoundedIcon from '@mui/icons-material/KeyRounded';
import MarkEmailReadRoundedIcon from '@mui/icons-material/MarkEmailReadRounded';
import { errorCode, errorMessage } from '@/api/client';
import { PasswordField } from '@/components/PasswordField';
import { useAuth } from '@/features/auth/AuthContext';
import type { CurrentUser } from '@/types/api';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const RESEND_SECONDS = 60;

/**
 * Forgotten password: ask for the email, then the emailed 6-digit code and a
 * new password. Success signs the user in and calls `onDone`.
 *
 * <p>The server answers the first step the same way whether or not the
 * account exists, so this screen never says which emails are registered.
 */
export function ForgotPasswordFlow({
  initialEmail = '',
  onDone,
  onCancel,
}: {
  initialEmail?: string;
  onDone: (user: CurrentUser) => void;
  onCancel: () => void;
}) {
  const { requestPasswordReset, resetPassword } = useAuth();
  const [step, setStep] = useState<'email' | 'code'>('email');
  const [email, setEmail] = useState(initialEmail);
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [showErrors, setShowErrors] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resendIn, setResendIn] = useState(0);

  useEffect(() => {
    if (resendIn <= 0) {
      return;
    }
    const timer = setTimeout(() => setResendIn((seconds) => seconds - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendIn]);

  const errors =
    step === 'email'
      ? { email: EMAIL.test(email.trim()) ? undefined : 'Enter a valid email address' }
      : {
          code: /^\d{6}$/.test(code) ? undefined : 'Enter the 6-digit code',
          password: password.length >= 8 ? undefined : 'Use at least 8 characters',
        };
  const errorOf = (key: string) => (showErrors ? (errors as Record<string, string | undefined>)[key] : undefined);

  const sendCode = async () => {
    await requestPasswordReset(email.trim());
    setResendIn(RESEND_SECONDS);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setShowErrors(true);
    setSubmitError(null);
    setNotice(null);
    if (Object.values(errors).some(Boolean)) {
      return;
    }
    setSubmitting(true);
    try {
      if (step === 'email') {
        await sendCode();
        setStep('code');
        setShowErrors(false);
      } else {
        onDone(await resetPassword(email.trim(), code, password));
      }
    } catch (error) {
      if (errorCode(error) === 'VERIFICATION_EXPIRED') {
        setCode('');
        setResendIn(0);
      }
      setSubmitError(errorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  const resend = async () => {
    setSubmitError(null);
    try {
      await sendCode();
      setCode('');
      setNotice('If there is an account for this email, we sent a new code.');
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  };

  if (step === 'email') {
    return (
      <Box component="form" onSubmit={submit} noValidate>
        <Stack spacing={1} alignItems="center" sx={{ textAlign: 'center', mb: 3 }}>
          <KeyRoundedIcon color="primary" sx={{ fontSize: 36 }} />
          <Typography variant="h6" fontWeight={800}>
            Forgot your password?
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Enter the email you signed up with and we will send you a code to choose a new password.
          </Typography>
        </Stack>
        <Stack spacing={2}>
          {submitError ? <Alert severity="error">{submitError}</Alert> : null}
          <TextField
            label="Email"
            type="email"
            autoComplete="email"
            autoFocus
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={Boolean(errorOf('email'))}
            helperText={errorOf('email')}
            fullWidth
          />
          <Button type="submit" variant="contained" size="large" disabled={submitting} sx={{ borderRadius: 999 }}>
            {submitting ? 'Sending…' : 'Send me a code'}
          </Button>
          <Link component="button" type="button" onClick={onCancel} sx={{ alignSelf: 'center' }}>
            Back to sign in
          </Link>
        </Stack>
      </Box>
    );
  }

  return (
    <Box component="form" onSubmit={submit} noValidate>
      <Stack spacing={1} alignItems="center" sx={{ textAlign: 'center', mb: 3 }}>
        <MarkEmailReadRoundedIcon color="primary" sx={{ fontSize: 40 }} />
        <Typography variant="h6" fontWeight={800}>
          Check your email
        </Typography>
        <Typography variant="body2" color="text.secondary">
          If there is an account for <strong>{email.trim()}</strong>, we sent it a 6-digit code. Check your spam folder
          if it does not arrive.
        </Typography>
      </Stack>
      <Stack spacing={2}>
        {submitError ? <Alert severity="error">{submitError}</Alert> : null}
        {notice ? <Alert severity="success">{notice}</Alert> : null}
        <TextField
          label="Code"
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
          autoFocus
          autoComplete="one-time-code"
          inputProps={{ inputMode: 'numeric', style: { letterSpacing: '0.5em', fontSize: 22, textAlign: 'center' } }}
          error={Boolean(errorOf('code'))}
          helperText={errorOf('code')}
          fullWidth
        />
        <PasswordField
          label="New password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={Boolean(errorOf('password'))}
          helperText={errorOf('password') ?? 'At least 8 characters'}
          fullWidth
        />
        <Button type="submit" variant="contained" size="large" disabled={submitting} sx={{ borderRadius: 999 }}>
          {submitting ? 'Saving…' : 'Set new password'}
        </Button>
        <Stack direction="row" justifyContent="space-between">
          <Link component="button" type="button" onClick={() => setStep('email')}>
            Use a different email
          </Link>
          <Link
            component="button"
            type="button"
            onClick={resend}
            disabled={resendIn > 0}
            sx={resendIn > 0 ? { color: 'text.disabled', pointerEvents: 'none', textDecoration: 'none' } : undefined}
          >
            {resendIn > 0 ? `Resend code in ${resendIn}s` : 'Resend code'}
          </Link>
        </Stack>
      </Stack>
    </Box>
  );
}
