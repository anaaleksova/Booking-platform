/**
 * Downloads page code ahead of time, while the browser is idle, so moving to
 * the next page is instant instead of waiting for its chunk. Vite dedupes
 * these imports with the lazy routes in App.tsx.
 */
const ADMIN_PAGES = [
  () => import('@/pages/admin/DashboardPage'),
  () => import('@/pages/admin/ReservationsPage'),
  () => import('@/pages/admin/ServicesPage'),
  () => import('@/pages/admin/ServiceEditorPage'),
  () => import('@/pages/admin/ResourcesPage'),
  () => import('@/pages/admin/ResourceTypesPage'),
  () => import('@/pages/admin/EmployeesPage'),
  () => import('@/pages/admin/CustomersPage'),
  () => import('@/pages/admin/CustomerDetailPage'),
  () => import('@/pages/admin/DiscountsPage'),
  () => import('@/pages/admin/RevenuePage'),
  () => import('@/pages/admin/AnalyticsPage'),
  () => import('@/pages/admin/IntegrationsPage'),
  () => import('@/pages/admin/SettingsPage'),
];

const CUSTOMER_PAGES = [
  () => import('@/pages/public/BusinessPage'),
  () => import('@/pages/public/BookingWizardPage'),
  () => import('@/pages/public/ReservationDetailsPage'),
];

function whenIdle(work: () => void) {
  const idle = (window as Window & { requestIdleCallback?: (callback: () => void) => number }).requestIdleCallback;
  if (idle) {
    idle(work);
  } else {
    setTimeout(work, 300);
  }
}

function prefetch(loaders: Array<() => Promise<unknown>>) {
  whenIdle(() => loaders.forEach((load) => load().catch(() => undefined)));
}

export const prefetchAdminPages = () => prefetch(ADMIN_PAGES);
export const prefetchCustomerPages = () => prefetch(CUSTOMER_PAGES);
