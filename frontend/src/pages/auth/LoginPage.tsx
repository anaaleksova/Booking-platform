import { useState } from 'react';
import { Link as RouterLink, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useAuth } from '@/features/auth/AuthContext';
import { errorMessage } from '@/api/client';
import { PasswordField } from '@/components/PasswordField';

const schema = z.object({
  email: z.string().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const { login, logout, isAuthenticated, initialising, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } });

  if (!initialising && isAuthenticated && user?.activeBusiness) {
    return <Navigate to="/admin" replace />;
  }
  const customerAccount = !initialising && isAuthenticated && !user?.activeBusiness;

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      const signedIn = await login(values.email, values.password);
      if (!signedIn.activeBusiness) {
        logout();
        setSubmitError('This is a customer account for booking. Sign in with a business account to open the dashboard.');
        return;
      }
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from ?? '/admin', { replace: true });
    } catch (error) {
      setSubmitError(errorMessage(error, 'Could not sign in.'));
    }
  });

  return (
    <AuthShell title="Welcome back" subtitle="Sign in to manage your business.">
      <form onSubmit={onSubmit} noValidate>
        <Stack spacing={2.5}>
          {submitError ? <Alert severity="error">{submitError}</Alert> : null}
          {customerAccount && !submitError ? (
            <Alert severity="info">
              You are signed in with a customer account, which can book but has no business dashboard.{' '}
              <Link component="button" type="button" onClick={logout}>
                Sign out
              </Link>
            </Alert>
          ) : null}
          <TextField
            label="Email"
            type="email"
            autoComplete="email"
            autoFocus
            fullWidth
            error={Boolean(errors.email)}
            helperText={errors.email?.message}
            {...register('email')}
          />
          <PasswordField
            label="Password"
            autoComplete="current-password"
            fullWidth
            error={Boolean(errors.password)}
            helperText={errors.password?.message}
            {...register('password')}
          />
          <Link component={RouterLink} to="/forgot-password" variant="body2" sx={{ alignSelf: 'flex-end', mt: -1 }}>
            Forgot password?
          </Link>
          <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </Button>
          <Typography variant="body2" color="text.secondary" textAlign="center">
            New here?{' '}
            <Link component={RouterLink} to="/register">
              Create a business account
            </Link>
          </Typography>
        </Stack>
      </form>
    </AuthShell>
  );
}

/** Shared frame for the two unauthenticated pages. */
export function AuthShell({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
}) {
  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        p: 2,
        background: 'linear-gradient(160deg, #eef2ff 0%, #f6f7fb 45%, #f2fbf7 100%)',
      }}
    >
      <Card sx={{ width: '100%', maxWidth: 460 }}>
        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Stack spacing={1} sx={{ mb: 3 }}>
            <Typography variant="h5">{title}</Typography>
            <Typography variant="body2" color="text.secondary">
              {subtitle}
            </Typography>
          </Stack>
          {children}
        </CardContent>
      </Card>
    </Box>
  );
}
