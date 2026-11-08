import { useState, type FormEvent } from 'react';
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
import { errorCode, errorMessage } from '@/api/client';
import { useAuth } from '@/features/auth/AuthContext';
import { PasswordField } from '@/components/PasswordField';
import { ForgotPasswordFlow } from '@/features/auth/ForgotPasswordFlow';
import { VerificationCodeForm } from '@/features/auth/VerificationCodeForm';
import { clearPendingSignup, loadPendingSignup, savePendingSignup } from '@/features/auth/pendingSignup';

type Mode = 'signup' | 'signin' | 'code' | 'forgot';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Shown in place of the details form until the customer is signed in. The
 * wizard's choices live in the URL, so nothing picked so far is lost.
 *
 * <p>Sign-up proves the email is real before an account exists: the server
 * emails a 6-digit code and only the right code creates the account.
 */
export function CustomerAuthPanel() {
  const { login, startCustomerSignup, verifyCustomerSignup, resendSignupCode } = useAuth();
  // A sign-up waiting for its code survives a reload (often while the customer fetches the email).
  const [waiting] = useState(() => loadPendingSignup('customer'));
  const [mode, setMode] = useState<Mode>(waiting ? 'code' : 'signup');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState(waiting?.email ?? '');
  const [password, setPassword] = useState('');
  const [showErrors, setShowErrors] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const errors = {
    fullName: mode === 'signup' && !fullName.trim() ? 'Enter your name' : undefined,
    email: EMAIL.test(email.trim()) ? undefined : 'Enter a valid email address',
    password: !password
      ? 'Enter a password'
      : mode === 'signup' && password.length < 8
        ? 'Use at least 8 characters'
        : undefined,
  };
  const errorOf = (key: string) => (showErrors ? (errors as Record<string, string | undefined>)[key] : undefined);

  const sendCode = () => startCustomerSignup({ fullName: fullName.trim(), email: email.trim(), password });

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setShowErrors(true);
    setSubmitError(null);
    if (Object.values(errors).some(Boolean)) {
      return;
    }
    setSubmitting(true);
    try {
      if (mode === 'signup') {
        await sendCode();
        savePendingSignup('customer', { email: email.trim() });
        setMode('code');
        setShowErrors(false);
      } else {
        await login(email.trim(), password);
      }
    } catch (error) {
      if (mode === 'signup' && errorCode(error) === 'EMAIL_ALREADY_REGISTERED') {
        switchMode('signin');
      }
      setSubmitError(errorMessage(error, 'Something went wrong. Please try again.'));
    } finally {
      setSubmitting(false);
    }
  };

  const switchMode = (next: Mode) => {
    setMode(next);
    setShowErrors(false);
    setSubmitError(null);
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
      <Box sx={{ maxWidth: 440, mx: 'auto' }}>
        <VerificationCodeForm
          email={email.trim()}
          onVerify={async (code) => {
            await verifyCustomerSignup(email.trim(), code);
            clearPendingSignup('customer');
          }}
          onResend={() => resendSignupCode(email.trim())}
          onBack={() => {
            clearPendingSignup('customer');
            switchMode('signup');
          }}
          buttonSx={{ borderRadius: 'var(--site-radius-button, 999px)' }}
        />
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
        <Button type="submit" variant="contained" size="large" disabled={submitting} sx={{ borderRadius: 'var(--site-radius-button, 999px)' }}>
          {submitting ? 'Please wait…' : mode === 'signup' ? 'Send me a code' : 'Sign in and continue'}
        </Button>
      </Stack>
    </Box>
  );
}
