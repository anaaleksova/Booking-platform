import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { apiClient, setSessionExpiredHandler, tokenStorage } from '@/api/client';
import type { AuthResponse, CurrentUser } from '@/types/api';

interface AuthContextValue {
  user: CurrentUser | null;
  /** True until the stored token has been checked against the server. */
  initialising: boolean;
  isAuthenticated: boolean;
  isOwner: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => void;
}

export interface RegisterInput {
  fullName: string;
  email: string;
  password: string;
  businessName: string;
  timezone: string;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Holds the session.
 *
 * <p>The token lives in localStorage so a refresh does not sign the user out;
 * on start-up it is verified against `/api/auth/me` rather than trusted, which
 * also picks up a role change made since the token was issued.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [initialising, setInitialising] = useState(true);

  const logout = useCallback(() => {
    tokenStorage.clear();
    setUser(null);
  }, []);

  useEffect(() => {
    setSessionExpiredHandler(() => setUser(null));
  }, []);

  useEffect(() => {
    const token = tokenStorage.get();
    if (!token) {
      setInitialising(false);
      return;
    }
    apiClient
      .get<CurrentUser>('/api/auth/me')
      .then((response) => setUser(response.data))
      .catch(() => tokenStorage.clear())
      .finally(() => setInitialising(false));
  }, []);

  const applySession = useCallback((data: AuthResponse) => {
    tokenStorage.set(data.accessToken);
    setUser(data.user);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const { data } = await apiClient.post<AuthResponse>('/api/auth/login', { email, password });
      applySession(data);
    },
    [applySession],
  );

  const register = useCallback(
    async (input: RegisterInput) => {
      const { data } = await apiClient.post<AuthResponse>('/api/auth/register', input);
      applySession(data);
    },
    [applySession],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      initialising,
      isAuthenticated: user !== null,
      isOwner: user?.activeBusiness?.role === 'BUSINESS_OWNER',
      login,
      register,
      logout,
    }),
    [user, initialising, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
