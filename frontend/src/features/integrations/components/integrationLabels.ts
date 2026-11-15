import type { IntegrationStatus } from '@/types/api';


/** Display labels for integration states. */
export const STATUS_CHIP: Record<IntegrationStatus, { label: string; color: 'success' | 'default' | 'warning' }> = {
  CONNECTED: { label: 'Connected', color: 'success' },
  NOT_CONNECTED: { label: 'Not connected', color: 'default' },
  DISABLED: { label: 'Disabled', color: 'warning' },
};
