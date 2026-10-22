import type { IntegrationEventDelivery, IntegrationPermission, IntegrationStatus } from '@/types/api';


/** Display labels for integration states, permissions and deliveries. */
export const STATUS_CHIP: Record<IntegrationStatus, { label: string; color: 'success' | 'default' | 'warning' }> = {
  CONNECTED: { label: 'Connected', color: 'success' },
  NOT_CONNECTED: { label: 'Not connected', color: 'default' },
  DISABLED: { label: 'Disabled', color: 'warning' },
};

export const PERMISSION_LABELS: Record<IntegrationPermission, string> = {
  READ_BUSINESS: 'Read business details',
  READ_SERVICES: 'Read services',
  READ_AVAILABILITY: 'Read availability and prices',
  CREATE_RESERVATION: 'Create reservations',
  READ_RESERVATION: 'Read reservations',
  CANCEL_RESERVATION: 'Cancel reservations',
  READ_CUSTOMER: 'Look up customers',
  CREATE_CUSTOMER: 'Create customers',
  MANAGE_CONVERSATIONS: 'Run chat conversations',
};

export const DELIVERY_COLORS: Record<IntegrationEventDelivery['status'], 'success' | 'default' | 'error' | 'info'> = {
  DELIVERED: 'success',
  PENDING: 'info',
  FAILED: 'error',
  SKIPPED: 'default',
};
