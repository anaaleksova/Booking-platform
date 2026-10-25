import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { LoadingState } from '@/components/states';
import { useAuth } from '@/features/auth/AuthContext';

/**
 * Gate for the platform area. Convenience only, like ProtectedRoute: the
 * platform API refuses everyone but platform administrators regardless.
 */
export function PlatformRoute({ children }: { children: ReactNode }) {
  const { user, initialising } = useAuth();
  const location = useLocation();

  if (initialising) {
    return <LoadingState label="Checking your session…" />;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (!user.platformAdmin) {
    return <Navigate to={user.activeBusiness ? '/admin' : '/login'} replace />;
  }
  return <>{children}</>;
}
