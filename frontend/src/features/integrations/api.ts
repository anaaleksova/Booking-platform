import { useMutation, useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { queryKeys } from '@/features/queryKeys';
import { useInvalidatingMutation } from '@/features/mutation';
import type {
  ChannelConnection,
  CreatedIntegrationToken,
  IntegrationPermission,
  IntegrationToken,
  IntegrationsOverview,
  N8nConnection,
  WebhookTestResult,
} from '@/types/api';

export function useIntegrations() {
  return useQuery({
    queryKey: queryKeys.integrations,
    queryFn: async () => (await apiClient.get<IntegrationsOverview>('/api/admin/integrations')).data,
  });
}

export function useSaveN8n() {
  return useInvalidatingMutation(
    async (input: { webhookUrl: string | null; enabled: boolean }) =>
      (await apiClient.put<N8nConnection>('/api/admin/integrations/n8n', input)).data,
    [queryKeys.integrations],
  );
}

export function useRotateSigningSecret() {
  return useInvalidatingMutation(
    async (_: void) => (await apiClient.post<N8nConnection>('/api/admin/integrations/n8n/rotate-secret')).data,
    [queryKeys.integrations],
  );
}

export function useSendTestEvent() {
  return useMutation({
    mutationFn: async () => (await apiClient.post<WebhookTestResult>('/api/admin/integrations/n8n/test')).data,
  });
}

export function useSaveChannel() {
  return useInvalidatingMutation(
    async ({
      provider,
      input,
    }: {
      provider: ChannelConnection['provider'];
      input: { externalAccountId: string | null; displayName: string | null; enabled: boolean };
    }) => (await apiClient.put<ChannelConnection>(`/api/admin/integrations/channels/${provider.toLowerCase()}`, input)).data,
    [queryKeys.integrations],
  );
}

export function useCreateToken() {
  return useInvalidatingMutation(
    async (input: { name: string; permissions: IntegrationPermission[] }) =>
      (await apiClient.post<CreatedIntegrationToken>('/api/admin/integrations/tokens', input)).data,
    [queryKeys.integrations],
  );
}

export function useRevokeToken() {
  return useInvalidatingMutation(
    async (id: number) => (await apiClient.delete<IntegrationToken>(`/api/admin/integrations/tokens/${id}`)).data,
    [queryKeys.integrations],
  );
}
