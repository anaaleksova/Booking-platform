/**
 * Every cache key in one place.
 *
 * <p>Keys are hierarchical, so invalidating `['bookings']` after a mutation
 * refreshes the calendar, the list and the dashboard together without any of
 * them knowing about each other.
 */
export const queryKeys = {
  business: ['business'] as const,
  businessSettings: ['business', 'settings'] as const,
  workingHours: ['working-hours'] as const,
  services: (activeOnly = false) => ['services', { activeOnly }] as const,
  staff: (activeOnly = false) => ['staff', { activeOnly }] as const,
  resources: (activeOnly = false) => ['resources', { activeOnly }] as const,
  customers: (query: string, page: number) => ['customers', { query, page }] as const,
  customer: (id: number) => ['customers', id] as const,
  customerHistory: (id: number) => ['customers', id, 'history'] as const,
  bookings: (filters: object) => ['bookings', filters] as const,
  booking: (id: number) => ['bookings', id] as const,
  dashboardStats: ['dashboard', 'stats'] as const,
  analyticsOverview: (from?: string, to?: string) => ['analytics', 'overview', { from, to }] as const,
  analyticsRevenueByService: (from?: string, to?: string) =>
    ['analytics', 'revenue-by-service', { from, to }] as const,
  analyticsCustomers: (from: string | undefined, to: string | undefined, page: number) =>
    ['analytics', 'customers', { from, to, page }] as const,
  blockedTimes: (from?: string, to?: string) => ['blocked-times', { from, to }] as const,
  availability: (filters: object) => ['availability', filters] as const,
  publicBusiness: (slug: string) => ['public', slug] as const,
  publicServices: (slug: string) => ['public', slug, 'services'] as const,
  publicStaff: (slug: string, serviceId: number) => ['public', slug, 'services', serviceId, 'staff'] as const,
  publicAvailability: (slug: string, filters: object) =>
    ['public', slug, 'availability', filters] as const,
  publicBooking: (reference: string) => ['public', 'bookings', reference] as const,
};
