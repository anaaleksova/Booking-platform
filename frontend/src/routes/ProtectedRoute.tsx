import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { LoadingState } from '@/components/states';

/**
 * Gate for the dashboard.
 *
 * <p>This is convenience, not security: every endpoint enforces authentication
 * and tenancy on its own. Hiding a route only saves the user a pointless
 * request.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, initialising, user } = useAuth();
  const location = useLocation();

  if (initialising) {
    return <LoadingState label="Checking your session…" />;
  }

  if (!isAuthenticated) {
    // Remember where they were headed so login can return them there.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  // A customer (booking-only) account has no business to manage.
  if (!user?.activeBusiness) {
    return <Navigate to="/login" replace state={{ from: location.pathname, customerAccount: true }} />;
  }

  return <>{children}</>;
}
