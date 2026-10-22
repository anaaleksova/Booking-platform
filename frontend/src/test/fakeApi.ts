import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { AxiosError } from 'axios';
import { apiClient } from '@/api/client';

export interface FakeReply {
  status: number;
  data?: unknown;
}

export interface RecordedRequest {
  method: string;
  url: string;
  data: unknown;
  headers: Record<string, unknown>;
  withCredentials: boolean | undefined;
}

/**
 * Replaces the HTTP layer of the real `apiClient`, so components and hooks
 * run their actual request code against canned answers.
 *
 * `routes` maps "METHOD /path" to a reply (or a function of the request).
 */
export function fakeApi(routes: Record<string, FakeReply | ((request: RecordedRequest) => FakeReply)>) {
  const requests: RecordedRequest[] = [];
  const original = apiClient.defaults.adapter;

  const adapter: AxiosAdapter = async (config: InternalAxiosRequestConfig) => {
    const request: RecordedRequest = {
      method: (config.method ?? 'get').toUpperCase(),
      url: config.url ?? '',
      data: typeof config.data === 'string' ? JSON.parse(config.data) : config.data,
      headers: { ...config.headers },
      withCredentials: config.withCredentials,
    };
    requests.push(request);
    const route = routes[`${request.method} ${request.url}`];
    if (!route) {
      throw new Error(`No fake reply for ${request.method} ${request.url}`);
    }
    const reply = typeof route === 'function' ? route(request) : route;
    const response: AxiosResponse = {
      data: reply.data ?? '',
      status: reply.status,
      statusText: String(reply.status),
      headers: {},
      config,
    };
    if (reply.status >= 400) {
      throw new AxiosError(`HTTP ${reply.status}`, String(reply.status), config, null, response);
    }
    return response;
  };
  apiClient.defaults.adapter = adapter;

  return {
    requests,
    restore: () => {
      apiClient.defaults.adapter = original;
    },
  };
}
