import axios, { AxiosError } from 'axios';
import type { ApiError } from '@/types/api';

const TOKEN_KEY = 'booking.accessToken';

/**
 * The one Axios instance in the application.
 *
 * <p>Base URL, the bearer header and error normalisation live here so no
 * component ever configures HTTP for itself.
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  headers: { 'Content-Type': 'application/json' },
  timeout: 20000,
});

export const tokenStorage = {
  get: (): string | null => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

apiClient.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
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

    // An expired or invalid token ends the session everywhere at once.
    if (status === 401 && (code === 'TOKEN_EXPIRED' || code === 'UNAUTHENTICATED')) {
      const hadToken = Boolean(tokenStorage.get());
      tokenStorage.clear();
      onSessionExpired?.();
      // A signed-in customer's stale token must not break browsing: public
      // reads work without one, so retry them once anonymously.
      const config = error.config;
      if (hadToken && config && config.url?.startsWith('/api/public/') && config.method === 'get') {
        delete config.headers.Authorization;
        return apiClient.request(config);
      }
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
