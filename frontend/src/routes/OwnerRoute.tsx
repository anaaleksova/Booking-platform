import { Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '@/features/auth/AuthContext';

/**
 * Owner-only admin pages. Staff land on their reservations instead — the
 * only page they have. Like ProtectedRoute this is convenience: the API
 * refuses staff on these endpoints regardless.
 */
export function OwnerRoute({ children }: { children: ReactNode }) {
  const { isOwner } = useAuth();
  return isOwner ? <>{children}</> : <Navigate to="/admin/reservations" replace />;
}
