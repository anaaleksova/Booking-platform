import { afterEach, describe, expect, it, vi } from 'vitest';
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { apiClient, errorCode, errorMessage, setSessionExpiredHandler } from '@/api/client';
import { fakeApi } from '@/test/fakeApi';

function axiosError(status: number | null, data?: unknown): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig;
  const response = status === null ? undefined : ({ status, data, config, headers: {} } as AxiosResponse);
  return new AxiosError('failed', 'ERR', config, null, response);
}

describe('errorMessage', () => {
  it('prefers the first field error', () => {
    const error = axiosError(400, { message: 'Request validation failed', fieldErrors: { email: 'must be valid' } });
    expect(errorMessage(error)).toBe('email: must be valid');
  });

  it('uses the server message', () => {
    expect(errorMessage(axiosError(409, { message: 'Taken.' }))).toBe('Taken.');
  });

  it('explains a network failure', () => {
    expect(errorMessage(axiosError(null))).toBe('Could not reach the server. Check your connection.');
  });

  it('falls back for anything else', () => {
    expect(errorMessage(new Error('boom'), 'Fallback')).toBe('Fallback');
  });

  it('exposes the machine-readable code', () => {
    expect(errorCode(axiosError(429, { error: 'TOO_MANY_REQUESTS' }))).toBe('TOO_MANY_REQUESTS');
    expect(errorCode(new Error('x'))).toBeUndefined();
  });
});

describe('apiClient', () => {
  let api: ReturnType<typeof fakeApi> | undefined;

  afterEach(() => {
    api?.restore();
    setSessionExpiredHandler(() => undefined);
  });

  it('sends the session cookie and the header the server requires for writes', async () => {
    api = fakeApi({ 'POST /api/admin/discounts': { status: 201, data: {} } });

    await apiClient.post('/api/admin/discounts', { code: 'TEN' });

    expect(api.requests[0].withCredentials).toBe(true);
    expect(api.requests[0].headers['X-Requested-With']).toBe('XMLHttpRequest');
    expect(api.requests[0].headers.Authorization).toBeUndefined();
  });

  it.each(['TOKEN_EXPIRED', 'UNAUTHENTICATED'])('ends the session on 401 %s', async (code) => {
    const expired = vi.fn();
    setSessionExpiredHandler(expired);
    api = fakeApi({ 'GET /api/admin/business': { status: 401, data: { error: code } } });

    await expect(apiClient.get('/api/admin/business')).rejects.toThrow();
    expect(expired).toHaveBeenCalledOnce();
  });

  it('does not end the session for a wrong password', async () => {
    const expired = vi.fn();
    setSessionExpiredHandler(expired);
    api = fakeApi({ 'POST /api/auth/login': { status: 401, data: { error: 'INVALID_CREDENTIALS' } } });

    await expect(apiClient.post('/api/auth/login', {})).rejects.toThrow();
    expect(expired).not.toHaveBeenCalled();
  });
});
