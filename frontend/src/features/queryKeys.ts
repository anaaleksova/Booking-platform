/**
 * Every cache key in one place.
 *
 * <p>Keys are hierarchical, so invalidating `['reservations']` after a
 * mutation refreshes the list, the calendar and the dashboard together without
 * any of them knowing about each other.
 */
export const queryKeys = {
  business: ['business'] as const,
  businessSettings: ['business', 'settings'] as const,
  openingHours: ['business', 'opening-hours'] as const,
  blockedTimes: ['business', 'blocked-times'] as const,
  gallery: ['business', 'gallery'] as const,

  services: ['services'] as const,
  service: (id: number) => ['services', id] as const,
  resourceTypes: ['resource-types'] as const,
  floorPlans: ['floor-plans'] as const,
  floorPlan: (typeId: number) => ['floor-plans', typeId] as const,
  resources: ['resources'] as const,
  employees: ['employees'] as const,

  customers: (query: string, page: number) => ['customers', { query, page }] as const,
  customer: (id: number) => ['customers', id] as const,
  customerHistory: (id: number) => ['customers', id, 'history'] as const,

  reservations: (filters: object) => ['reservations', filters] as const,
  reservation: (id: number) => ['reservations', id] as const,
  reservationOptions: (id: number) => ['reservations', id, 'options'] as const,
  adminAvailability: (filters: object) => ['reservations', 'availability', filters] as const,

  discounts: ['discounts'] as const,

  integrations: ['integrations'] as const,

  dashboard: ['analytics', 'dashboard'] as const,
  revenuePeriods: ['analytics', 'revenue-periods'] as const,
  revenue: (filters: object) => ['analytics', 'revenue', filters] as const,
  reservationStats: (filters: object) => ['analytics', 'reservations', filters] as const,
  serviceStats: (filters: object) => ['analytics', 'services', filters] as const,
  resourceStats: (filters: object) => ['analytics', 'resources', filters] as const,
  customerStats: (filters: object) => ['analytics', 'customers', filters] as const,
  sourceStats: (filters: object) => ['analytics', 'sources', filters] as const,

  publicBusiness: (slug: string) => ['public', slug] as const,
  publicServices: (slug: string) => ['public', slug, 'services'] as const,
  publicService: (slug: string, id: number) => ['public', slug, 'services', id] as const,
  publicAvailability: (slug: string, filters: object) => ['public', slug, 'availability', filters] as const,
  publicReservation: (reference: string) => ['public', 'reservations', reference] as const,
};
