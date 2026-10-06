import { useEffect, useState, type FormEvent } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import LockRoundedIcon from '@mui/icons-material/LockRounded';
import MarkEmailReadRoundedIcon from '@mui/icons-material/MarkEmailReadRounded';
import { errorCode, errorMessage } from '@/api/client';
import { useAuth } from '@/features/auth/AuthContext';
import { PasswordField } from '@/components/PasswordField';
import { ForgotPasswordFlow } from '@/features/auth/ForgotPasswordFlow';

type Mode = 'signup' | 'signin' | 'code' | 'forgot';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const RESEND_SECONDS = 60;

/**
 * Shown in place of the details form until the customer is signed in. The
 * wizard's choices live in the URL, so nothing picked so far is lost.
 *
 * <p>Sign-up proves the email is real before an account exists: the server
 * emails a 6-digit code and only the right code creates the account.
 */
export function CustomerAuthPanel() {
  const { login, startCustomerSignup, verifyCustomerSignup } = useAuth();
  const [mode, setMode] = useState<Mode>('signup');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
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
    mode === 'code'
      ? { code: /^\d{6}$/.test(code) ? undefined : 'Enter the 6-digit code' }
      : {
          fullName: mode === 'signup' && !fullName.trim() ? 'Enter your name' : undefined,
          email: EMAIL.test(email.trim()) ? undefined : 'Enter a valid email address',
          password: !password
            ? 'Enter a password'
            : mode === 'signup' && password.length < 8
              ? 'Use at least 8 characters'
              : undefined,
        };
  const errorOf = (key: string) => (showErrors ? (errors as Record<string, string | undefined>)[key] : undefined);

  const sendCode = async () => {
    await startCustomerSignup({ fullName: fullName.trim(), email: email.trim(), password });
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
      if (mode === 'signup') {
        await sendCode();
        setMode('code');
        setShowErrors(false);
      } else if (mode === 'code') {
        await verifyCustomerSignup(email.trim(), code);
      } else {
        await login(email.trim(), password);
      }
    } catch (error) {
      if (mode === 'code' && errorCode(error) === 'VERIFICATION_EXPIRED') {
        setCode('');
        setResendIn(0);
      }
      if (mode === 'signup' && errorCode(error) === 'EMAIL_ALREADY_REGISTERED') {
        switchMode('signin');
      }
      setSubmitError(errorMessage(error, 'Something went wrong. Please try again.'));
    } finally {
      setSubmitting(false);
    }
  };

  const resend = async () => {
    setSubmitError(null);
    setNotice(null);
    try {
      await sendCode();
      setCode('');
      setNotice('We sent you a new code.');
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  };

  const switchMode = (next: Mode) => {
    setMode(next);
    setShowErrors(false);
    setSubmitError(null);
    setNotice(null);
  };

  if (mode === 'forgot') {
    // Success signs the customer in, which replaces this panel with the details form.
    return (
      <Box sx={{ maxWidth: 440, mx: 'auto' }}>
        <ForgotPasswordFlow initialEmail={email} onDone={() => undefined} onCancel={() => switchMode('signin')} />
      </Box>
    );
  }

  if (mode === 'code') {
    return (
      <Box component="form" onSubmit={submit} noValidate sx={{ maxWidth: 440, mx: 'auto' }}>
        <Stack spacing={1} alignItems="center" sx={{ textAlign: 'center', mb: 3 }}>
          <MarkEmailReadRoundedIcon color="primary" sx={{ fontSize: 40 }} />
          <Typography variant="h6" fontWeight={800}>
            Check your email
          </Typography>
          <Typography variant="body2" color="text.secondary">
            We sent a 6-digit code to <strong>{email.trim()}</strong>. Enter it below to create your account. Check
            your spam folder if it does not arrive.
          </Typography>
        </Stack>
        <Stack spacing={2}>
          {submitError ? <Alert severity="error">{submitError}</Alert> : null}
          {notice ? <Alert severity="success">{notice}</Alert> : null}
          <TextField
            label="Verification code"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            autoFocus
            autoComplete="one-time-code"
            inputProps={{ inputMode: 'numeric', style: { letterSpacing: '0.5em', fontSize: 22, textAlign: 'center' } }}
            error={Boolean(errorOf('code'))}
            helperText={errorOf('code')}
            fullWidth
          />
          <Button type="submit" variant="contained" size="large" disabled={submitting} sx={{ borderRadius: 999 }}>
            {submitting ? 'Checking…' : 'Verify and continue'}
          </Button>
          <Stack direction="row" justifyContent="space-between">
            <Link component="button" type="button" onClick={() => switchMode('signup')}>
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

  return (
    <Box component="form" onSubmit={submit} noValidate sx={{ maxWidth: 440, mx: 'auto' }}>
      <Stack spacing={1} alignItems="center" sx={{ textAlign: 'center', mb: 2 }}>
        <LockRoundedIcon color="primary" />
        <Typography variant="h6" fontWeight={800}>
          {mode === 'signup' ? 'Create an account to book' : 'Sign in to book'}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Your choices are saved. You only need to do this once.
        </Typography>
      </Stack>
      <Tabs value={mode} onChange={(_, next: Mode) => switchMode(next)} variant="fullWidth" sx={{ mb: 2.5 }}>
        <Tab value="signup" label="Create account" />
        <Tab value="signin" label="Sign in" />
      </Tabs>
      <Stack spacing={2}>
        {submitError ? <Alert severity="error">{submitError}</Alert> : null}
        {mode === 'signup' ? (
          <TextField
            label="Full name"
            autoComplete="name"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            error={Boolean(errorOf('fullName'))}
            helperText={errorOf('fullName')}
            fullWidth
          />
        ) : null}
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={Boolean(errorOf('email'))}
          helperText={errorOf('email') ?? (mode === 'signup' ? 'We will send a code to confirm it.' : undefined)}
          fullWidth
        />
        <PasswordField
          label="Password"
          autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={Boolean(errorOf('password'))}
          helperText={errorOf('password') ?? (mode === 'signup' ? 'At least 8 characters' : undefined)}
          fullWidth
        />
        {mode === 'signin' ? (
          <Link component="button" type="button" onClick={() => switchMode('forgot')} sx={{ alignSelf: 'flex-end', mt: -1 }}>
            Forgot password?
          </Link>
        ) : null}
        <Button type="submit" variant="contained" size="large" disabled={submitting} sx={{ borderRadius: 999 }}>
          {submitting ? 'Please wait…' : mode === 'signup' ? 'Send me a code' : 'Sign in and continue'}
        </Button>
      </Stack>
    </Box>
  );
}
