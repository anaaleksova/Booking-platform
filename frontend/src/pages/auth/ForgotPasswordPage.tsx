import { useNavigate } from 'react-router-dom';
import { ForgotPasswordFlow } from '@/features/auth/ForgotPasswordFlow';
import { AuthShell } from '@/pages/auth/LoginPage';

/** Forgotten password for the dashboard sign-in. Customers reset from the booking page. */
export function ForgotPasswordPage() {
  const navigate = useNavigate();
  return (
    <AuthShell title="Reset your password" subtitle="It only takes a minute.">
      <ForgotPasswordFlow
        onDone={(user) => navigate(user.activeBusiness ? '/admin' : '/login', { replace: true })}
        onCancel={() => navigate('/login')}
      />
    </AuthShell>
  );
}
