import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { apiClient, setSessionExpiredHandler } from '@/api/client';
import type { AuthResponse, BusinessType, CurrentUser, SignupStarted } from '@/types/api';

interface AuthContextValue {
  user: CurrentUser | null;
  /** True until the server has said whether there is a session. */
  initialising: boolean;
  isAuthenticated: boolean;
  isOwner: boolean;
  /** Resolves to the signed-in user, so callers can tell a customer account from a business one. */
  login: (email: string, password: string) => Promise<CurrentUser>;
  /**
   * Business sign-up, step 1: emails a 6-digit code. Nothing is created yet.
   * Resolves to whether the email already has an account (from booking), which
   * the code then turns into the business's, with the password just entered.
   */
  register: (input: RegisterInput) => Promise<SignupStarted>;
  /** Emails a new code for a sign-up (either kind) still waiting for one. Needs only the email. */
  resendSignupCode: (email: string) => Promise<void>;
  /** Step 2: the code creates the account and the business, and signs in. */
  verifyRegistration: (email: string, code: string) => Promise<void>;
  /** Booking-only sign-up, step 1: emails a 6-digit code. No account yet. */
  startCustomerSignup: (input: CustomerRegisterInput) => Promise<void>;
  /** Step 2: the code creates the account (no business) and signs in. */
  verifyCustomerSignup: (email: string, code: string) => Promise<void>;
  /** Forgotten password, step 1: emails a code if the account exists. */
  requestPasswordReset: (email: string) => Promise<void>;
  /** Step 2: the code sets the new password and signs in. */
  resetPassword: (email: string, code: string, newPassword: string) => Promise<CurrentUser>;
  /** Ends the session at once here, and resolves when the server has cleared the cookie. */
  logout: () => Promise<void>;
  /** Signs in with a session another endpoint issued (e.g. accepting a staff invitation). */
  adoptSession: (data: AuthResponse) => void;
}

export interface RegisterInput {
  fullName: string;
  email: string;
  password: string;
  businessName: string;
  timezone: string;
  businessType: BusinessType;
  currency: string;
}

export interface CustomerRegisterInput {
  fullName: string;
  email: string;
  password: string;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Holds the signed-in user.
 *
 * <p>The session itself is an httpOnly cookie that only the server reads and
 * writes. On start-up `/api/auth/session` says who it belongs to (or 204 for
 * nobody), which also picks up a role change made since sign-in.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [initialising, setInitialising] = useState(true);

  const logout = useCallback(async () => {
    setUser(null);
    try {
      await apiClient.post('/api/auth/logout');
    } catch {
      // Signed out here regardless; the cookie expires on its own.
    }
  }, []);

  useEffect(() => {
    setSessionExpiredHandler(() => setUser(null));
  }, []);

  useEffect(() => {
    apiClient
      .get<CurrentUser | ''>('/api/auth/session')
      .then((response) => setUser(response.status === 200 && response.data ? response.data : null))
      .catch(() => setUser(null))
      .finally(() => setInitialising(false));
  }, []);

  const applySession = useCallback((data: AuthResponse) => {
    setUser(data.user);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const { data } = await apiClient.post<AuthResponse>('/api/auth/login', { email, password });
      applySession(data);
      return data.user;
    },
    [applySession],
  );

  const register = useCallback(async (input: RegisterInput) => {
    return (await apiClient.post<SignupStarted>('/api/auth/register', input)).data;
  }, []);

  const resendSignupCode = useCallback(async (email: string) => {
    await apiClient.post('/api/auth/signup/resend', { email });
  }, []);

  const verifyRegistration = useCallback(
    async (email: string, code: string) => {
      const { data } = await apiClient.post<AuthResponse>('/api/auth/register/verify', { email, code });
      applySession(data);
    },
    [applySession],
  );

  const startCustomerSignup = useCallback(async (input: CustomerRegisterInput) => {
    await apiClient.post('/api/auth/customer/register', input);
  }, []);

  const verifyCustomerSignup = useCallback(
    async (email: string, code: string) => {
      const { data } = await apiClient.post<AuthResponse>('/api/auth/customer/verify', { email, code });
      applySession(data);
    },
    [applySession],
  );

  const requestPasswordReset = useCallback(async (email: string) => {
    await apiClient.post('/api/auth/password/forgot', { email });
  }, []);

  const resetPassword = useCallback(
    async (email: string, code: string, newPassword: string) => {
      const { data } = await apiClient.post<AuthResponse>('/api/auth/password/reset', { email, code, newPassword });
      applySession(data);
      return data.user;
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
      verifyRegistration,
      startCustomerSignup,
      verifyCustomerSignup,
      requestPasswordReset,
      resetPassword,
      logout,
      adoptSession: applySession,
      resendSignupCode,
    }),
    [
      user,
      initialising,
      login,
      register,
      resendSignupCode,
      verifyRegistration,
      startCustomerSignup,
      verifyCustomerSignup,
      requestPasswordReset,
      resetPassword,
      logout,
      applySession,
    ],
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
