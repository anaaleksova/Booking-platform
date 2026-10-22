import { useEffect, useState, type FormEvent } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import type { SxProps, Theme } from '@mui/material/styles';
import MarkEmailReadRoundedIcon from '@mui/icons-material/MarkEmailReadRounded';
import { errorCode, errorMessage } from '@/api/client';

/** The server allows a new code once a minute. */
export const RESEND_SECONDS = 60;

interface VerificationCodeFormProps {
  /** Where the code was sent. */
  email: string;
  /** Confirms the code; success signs the person in. */
  onVerify: (code: string) => Promise<void>;
  /** Asks for a new code. */
  onResend: () => Promise<void>;
  /** Back to the details, e.g. to correct the email. */
  onBack: () => void;
  /** What the code finishes, e.g. "create your account". */
  purpose?: string;
  buttonSx?: SxProps<Theme>;
}

/**
 * "Check your email": the 6-digit code that proves an address before an
 * account exists. Used by owner registration and by customer sign-up.
 */
export function VerificationCodeForm({
  email,
  onVerify,
  onResend,
  onBack,
  purpose = 'create your account',
  buttonSx,
}: VerificationCodeFormProps) {
  const [code, setCode] = useState('');
  const [showErrors, setShowErrors] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // The first code was just sent by the step before this one.
  const [resendIn, setResendIn] = useState(RESEND_SECONDS);

  useEffect(() => {
    if (resendIn <= 0) {
      return;
    }
    const timer = setTimeout(() => setResendIn((seconds) => seconds - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendIn]);

  const codeError = /^\d{6}$/.test(code) ? undefined : 'Enter the 6-digit code';

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setShowErrors(true);
    setSubmitError(null);
    setNotice(null);
    if (codeError) {
      return;
    }
    setSubmitting(true);
    try {
      await onVerify(code);
    } catch (error) {
      if (errorCode(error) === 'VERIFICATION_EXPIRED') {
        setCode('');
        setResendIn(0);
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
      await onResend();
      setCode('');
      setResendIn(RESEND_SECONDS);
      setNotice('We sent you a new code.');
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  };

  return (
    <Box component="form" onSubmit={submit} noValidate>
      <Stack spacing={1} alignItems="center" sx={{ textAlign: 'center', mb: 3 }}>
        <MarkEmailReadRoundedIcon color="primary" sx={{ fontSize: 40 }} />
        <Typography variant="h6" fontWeight={800}>
          Check your email
        </Typography>
        <Typography variant="body2" color="text.secondary">
          We sent a 6-digit code to <strong>{email}</strong>. Enter it below to {purpose}. Check your spam folder if
          it does not arrive.
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
          error={showErrors && Boolean(codeError)}
          helperText={showErrors ? codeError : undefined}
          fullWidth
        />
        <Button type="submit" variant="contained" size="large" disabled={submitting} sx={buttonSx}>
          {submitting ? 'Checking…' : 'Verify and continue'}
        </Button>
        <Stack direction="row" justifyContent="space-between">
          <Link component="button" type="button" onClick={onBack}>
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
