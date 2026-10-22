import axios, { AxiosError } from 'axios';
import type { ApiError } from '@/types/api';

/**
 * The one Axios instance in the application.
 *
 * <p>Base URL, credentials and error normalisation live here so no component
 * ever configures HTTP for itself.
 *
 * <p>The session is an httpOnly cookie the server sets on sign-in: no script,
 * ours or an injected one, can read it. `withCredentials` sends it, and
 * `X-Requested-With` is what the server requires before it accepts a write
 * made with that cookie, a header no cross-site form can add.
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
  withCredentials: true,
  timeout: 20000,
});

/** Called when the server says the session is over. Set by the auth provider. */
let onSessionExpired: (() => void) | null = null;

export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler;
}

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    const status = error.response?.status;
    const code = error.response?.data?.error;

    // An expired or invalid session ends it everywhere at once. The server has
    // already cleared the cookie, and public pages keep working without it.
    if (status === 401 && (code === 'TOKEN_EXPIRED' || code === 'UNAUTHENTICATED')) {
      onSessionExpired?.();
    }
    return Promise.reject(error);
  },
);

/** Turns any failure into a message worth showing a human. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (axios.isAxiosError<ApiError>(error)) {
    const data = error.response?.data;
    if (data?.fieldErrors) {
      const first = Object.entries(data.fieldErrors)[0];
      if (first) {
        return `${first[0]}: ${first[1]}`;
      }
    }
    if (data?.message) {
      return data.message;
    }
    if (!error.response) {
      return 'Could not reach the server. Check your connection.';
    }
  }
  return fallback;
}

export function errorCode(error: unknown): string | undefined {
  return axios.isAxiosError<ApiError>(error) ? error.response?.data?.error : undefined;
}
