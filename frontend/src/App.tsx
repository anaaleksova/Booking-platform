import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { LoadingState } from '@/components/states';

/**
 * Two experiences, one app.
 *
 * <p>`/dashboard/*` is the business interface and requires a session;
 * `/book/:slug` and `/booking/:reference` are the customer-facing pages and
 * must stay reachable without one.
 *
 * <p>Everything below is lazy-loaded, so a customer opening a booking link on
 * a phone downloads the booking flow — not the calendar, the tables and the
 * date pickers behind the dashboard.
 */

const LoginPage = lazy(() => import('@/pages/auth/LoginPage').then((m) => ({ default: m.LoginPage })));
const RegisterPage = lazy(() =>
  import('@/pages/auth/RegisterPage').then((m) => ({ default: m.RegisterPage })),
);
const PublicBookingPage = lazy(() =>
  import('@/pages/booking/PublicBookingPage').then((m) => ({ default: m.PublicBookingPage })),
);
const BookingConfirmationPage = lazy(() =>
  import('@/pages/booking/BookingConfirmationPage').then((m) => ({ default: m.BookingConfirmationPage })),
);
const DashboardLayout = lazy(() =>
  import('@/layouts/DashboardLayout').then((m) => ({ default: m.DashboardLayout })),
);
const DashboardHomePage = lazy(() =>
  import('@/pages/dashboard/DashboardHomePage').then((m) => ({ default: m.DashboardHomePage })),
);
const CalendarPage = lazy(() =>
  import('@/pages/dashboard/CalendarPage').then((m) => ({ default: m.CalendarPage })),
);
const BookingsPage = lazy(() =>
  import('@/pages/dashboard/BookingsPage').then((m) => ({ default: m.BookingsPage })),
);
const ServicesPage = lazy(() =>
  import('@/pages/dashboard/ServicesPage').then((m) => ({ default: m.ServicesPage })),
);
const StaffPage = lazy(() => import('@/pages/dashboard/StaffPage').then((m) => ({ default: m.StaffPage })));
const ResourcesPage = lazy(() =>
  import('@/pages/dashboard/ResourcesPage').then((m) => ({ default: m.ResourcesPage })),
);
const CustomersPage = lazy(() =>
  import('@/pages/dashboard/CustomersPage').then((m) => ({ default: m.CustomersPage })),
);
const CustomerDetailPage = lazy(() =>
  import('@/pages/dashboard/CustomerDetailPage').then((m) => ({ default: m.CustomerDetailPage })),
);
const ReportsPage = lazy(() => import('@/pages/dashboard/ReportsPage').then((m) => ({ default: m.ReportsPage })));
const AvailabilityPage = lazy(() =>
  import('@/pages/dashboard/AvailabilityPage').then((m) => ({ default: m.AvailabilityPage })),
);
const SettingsPage = lazy(() =>
  import('@/pages/dashboard/SettingsPage').then((m) => ({ default: m.SettingsPage })),
);
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })));

export function App() {
  return (
    <Suspense fallback={<LoadingState />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route path="/book/:slug" element={<PublicBookingPage />} />
        <Route path="/booking/:reference" element={<BookingConfirmationPage />} />

        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <DashboardLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<DashboardHomePage />} />
          <Route path="calendar" element={<CalendarPage />} />
          <Route path="bookings" element={<BookingsPage />} />
          <Route path="services" element={<ServicesPage />} />
          <Route path="staff" element={<StaffPage />} />
          <Route path="resources" element={<ResourcesPage />} />
          <Route path="customers" element={<CustomersPage />} />
          <Route path="customers/:id" element={<CustomerDetailPage />} />
          <Route path="reports" element={<ReportsPage />} />
          <Route path="availability" element={<AvailabilityPage />} />
          <Route path="settings" element={<SettingsPage />} />
        </Route>

        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}
