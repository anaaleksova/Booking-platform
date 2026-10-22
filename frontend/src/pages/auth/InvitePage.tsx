import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { errorMessage } from '@/api/client';
import { PasswordField } from '@/components/PasswordField';
import { LoadingState } from '@/components/states';
import { useAuth } from '@/features/auth/AuthContext';
import { acceptAsSignedIn, acceptWithNewAccount, useInvitation } from '@/features/staff/api';
import { AuthShell } from '@/pages/auth/LoginPage';

/**
 * Where the invitation email's link lands. A new address sets a name and a
 * password; an address that already has an account signs in with it. Either
 * way the person ends up on their reservations.
 */
export function InvitePage() {
  const token = useParams().token!;
  const navigate = useNavigate();
  const { user, login, logout, adoptSession } = useAuth();
  const { data: invitation, isLoading, isError, error } = useInvitation(token);
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  if (isLoading) {
    return <LoadingState />;
  }
  if (isError || !invitation) {
    return (
      <AuthShell title="Invitation not valid" subtitle="It may have expired or been withdrawn.">
        <Alert severity="warning">{errorMessage(error, 'Ask the business to send you a new invitation.')}</Alert>
      </AuthShell>
    );
  }

  const signedInAsInvited = user?.email.toLowerCase() === invitation.email.toLowerCase();
  const finish = async (accept: () => Promise<void>) => {
    setSubmitError(null);
    setSubmitting(true);
    try {
      await accept();
      navigate('/admin/reservations', { replace: true });
    } catch (err) {
      setSubmitError(errorMessage(err, 'Could not accept the invitation.'));
    } finally {
      setSubmitting(false);
    }
  };

  const subtitle = `${invitation.businessName} invited you, ${invitation.employeeName}, to see the reservations assigned to you.`;

  // Already signed in with the invited address: one click.
  if (signedInAsInvited) {
    return (
      <AuthShell title={`Join ${invitation.businessName}`} subtitle={subtitle}>
        <Stack spacing={2}>
          {submitError ? <Alert severity="error">{submitError}</Alert> : null}
          <Button
            variant="contained"
            size="large"
            disabled={submitting}
            onClick={() => finish(async () => adoptSession(await acceptAsSignedIn(token)))}
          >
            Accept invitation
          </Button>
        </Stack>
      </AuthShell>
    );
  }

  return (
    <AuthShell title={`Join ${invitation.businessName}`} subtitle={subtitle}>
      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          if (invitation.accountExists) {
            // Sign in with the existing account, then accept with it.
            finish(async () => {
              await login(invitation.email, password);
              adoptSession(await acceptAsSignedIn(token));
            });
          } else {
            finish(async () => adoptSession(await acceptWithNewAccount(token, fullName.trim(), password)));
          }
        }}
      >
        <Stack spacing={2.5}>
          {submitError ? <Alert severity="error">{submitError}</Alert> : null}
          {user && !signedInAsInvited ? (
            <Alert severity="info">
              You are signed in as {user.email}. This invitation is for {invitation.email}.{' '}
              <Link component="button" type="button" onClick={logout}>
                Sign out
              </Link>
            </Alert>
          ) : null}
          <TextField label="Email" value={invitation.email} fullWidth disabled />
          {invitation.accountExists ? (
            <>
              <Typography variant="body2" color="text.secondary">
                You already have an account with this address. Enter its password to accept.
              </Typography>
              <PasswordField
                label="Password"
                autoComplete="current-password"
                autoFocus
                fullWidth
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              <Link component={RouterLink} to="/forgot-password" variant="body2" sx={{ alignSelf: 'flex-end', mt: -1 }}>
                Forgot password?
              </Link>
            </>
          ) : (
            <>
              <TextField
                label="Your name"
                autoComplete="name"
                autoFocus
                fullWidth
                value={fullName}
                onChange={(event) => setFullName(event.target.value)}
              />
              <PasswordField
                label="Choose a password"
                autoComplete="new-password"
                fullWidth
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                helperText="At least 8 characters"
              />
            </>
          )}
          <Button
            type="submit"
            variant="contained"
            size="large"
            disabled={
              submitting || (invitation.accountExists ? !password : !fullName.trim() || password.length < 8)
            }
          >
            {submitting ? 'Joining…' : 'Accept invitation'}
          </Button>
        </Stack>
      </form>
    </AuthShell>
  );
}
