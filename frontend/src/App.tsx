import { Suspense, lazy, type ComponentType } from 'react';
import { Navigate, Route, Routes, useParams } from 'react-router-dom';
import { OwnerRoute } from '@/routes/OwnerRoute';
import { PlatformRoute } from '@/routes/PlatformRoute';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { LoadingState } from '@/components/states';

/**
 * Two experiences, one app.
 *
 * <p>`/admin/*` is the business interface and requires a session.
 * `/b/:slug` (the business's own page), `/b/:slug/book` (the booking wizard)
 * and `/booking/:reference` are the customer pages and need none. Each
 * business is reached only through its own link — nothing lists them all.
 *
 * <p>Everything is lazy-loaded: a customer opening a booking link on a phone
 * downloads the booking flow, not the admin tables and charts.
 */
function page<T, K extends keyof T>(loader: () => Promise<T>, name: K) {
  return lazy(() => loader().then((module) => ({ default: module[name] as ComponentType })));
}

const LoginPage = page(() => import('@/pages/auth/LoginPage'), 'LoginPage');
const ForgotPasswordPage = page(() => import('@/pages/auth/ForgotPasswordPage'), 'ForgotPasswordPage');
const RegisterPage = page(() => import('@/pages/auth/RegisterPage'), 'RegisterPage');
const InvitePage = page(() => import('@/pages/auth/InvitePage'), 'InvitePage');
const NotFoundPage = page(() => import('@/pages/NotFoundPage'), 'NotFoundPage');

const HomePage = page(() => import('@/pages/public/HomePage'), 'HomePage');
const BusinessPage = page(() => import('@/pages/public/BusinessPage'), 'BusinessPage');
const BookingWizardPage = page(() => import('@/pages/public/BookingWizardPage'), 'BookingWizardPage');
const ReservationDetailsPage = page(() => import('@/pages/public/ReservationDetailsPage'), 'ReservationDetailsPage');

const AdminLayout = page(() => import('@/layouts/AdminLayout'), 'AdminLayout');
const DashboardPage = page(() => import('@/pages/admin/DashboardPage'), 'DashboardPage');
const ReservationsPage = page(() => import('@/pages/admin/ReservationsPage'), 'ReservationsPage');
const ServicesPage = page(() => import('@/pages/admin/ServicesPage'), 'ServicesPage');
const ServiceEditorPage = page(() => import('@/pages/admin/ServiceEditorPage'), 'ServiceEditorPage');
const ResourcesPage = page(() => import('@/pages/admin/ResourcesPage'), 'ResourcesPage');
const ResourceTypesPage = page(() => import('@/pages/admin/ResourceTypesPage'), 'ResourceTypesPage');
const EmployeesPage = page(() => import('@/pages/admin/EmployeesPage'), 'EmployeesPage');
const CustomersPage = page(() => import('@/pages/admin/CustomersPage'), 'CustomersPage');
const CustomerDetailPage = page(() => import('@/pages/admin/CustomerDetailPage'), 'CustomerDetailPage');
const DiscountsPage = page(() => import('@/pages/admin/DiscountsPage'), 'DiscountsPage');
const EventsPage = page(() => import('@/pages/admin/EventsPage'), 'EventsPage');
const RevenuePage = page(() => import('@/pages/admin/RevenuePage'), 'RevenuePage');
const AnalyticsPage = page(() => import('@/pages/admin/AnalyticsPage'), 'AnalyticsPage');
const SettingsPage = page(() => import('@/pages/admin/SettingsPage'), 'SettingsPage');
const TimeOffPage = page(() => import('@/pages/admin/TimeOffPage'), 'TimeOffPage');
const IntegrationsPage = page(() => import('@/pages/admin/IntegrationsPage'), 'IntegrationsPage');
const PlatformPage = page(() => import('@/pages/platform/PlatformPage'), 'PlatformPage');

function ToWizard() {
  const { slug, serviceId } = useParams();
  return <Navigate to={`/b/${slug}/book?service=${serviceId}`} replace />;
}

export function App() {
  return (
    <Suspense fallback={<LoadingState />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/invite/:token" element={<InvitePage />} />

        <Route path="/" element={<HomePage />} />
        <Route path="/b/:slug" element={<BusinessPage />} />
        <Route path="/b/:slug/book" element={<BookingWizardPage />} />
        {/* Older links that named the service in the path. */}
        <Route path="/b/:slug/book/:serviceId" element={<ToWizard />} />
        <Route path="/b/:slug/services/:serviceId" element={<ToWizard />} />
        <Route path="/booking/:reference" element={<ReservationDetailsPage />} />

        <Route
          path="/admin"
          element={
            <ProtectedRoute>
              <AdminLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<OwnerRoute><DashboardPage /></OwnerRoute>} />
          <Route path="reservations" element={<ReservationsPage />} />
          <Route path="time-off" element={<TimeOffPage />} />
          <Route path="services" element={<OwnerRoute><ServicesPage /></OwnerRoute>} />
          <Route path="services/new" element={<OwnerRoute><ServiceEditorPage /></OwnerRoute>} />
          <Route path="services/:id" element={<OwnerRoute><ServiceEditorPage /></OwnerRoute>} />
          <Route path="resources" element={<OwnerRoute><ResourcesPage /></OwnerRoute>} />
          <Route path="resource-types" element={<OwnerRoute><ResourceTypesPage /></OwnerRoute>} />
          <Route path="employees" element={<OwnerRoute><EmployeesPage /></OwnerRoute>} />
          <Route path="customers" element={<OwnerRoute><CustomersPage /></OwnerRoute>} />
          <Route path="customers/:id" element={<OwnerRoute><CustomerDetailPage /></OwnerRoute>} />
          <Route path="discounts" element={<OwnerRoute><DiscountsPage /></OwnerRoute>} />
          <Route path="events" element={<OwnerRoute><EventsPage /></OwnerRoute>} />
          <Route path="revenue" element={<OwnerRoute><RevenuePage /></OwnerRoute>} />
          <Route path="analytics" element={<OwnerRoute><AnalyticsPage /></OwnerRoute>} />
          <Route path="integrations" element={<OwnerRoute><IntegrationsPage /></OwnerRoute>} />
          <Route path="settings" element={<OwnerRoute><SettingsPage /></OwnerRoute>} />
        </Route>

        {/* Running the platform itself: every business and account. */}
        <Route path="/platform" element={<PlatformRoute><PlatformPage /></PlatformRoute>} />

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}
