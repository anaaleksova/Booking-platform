/**
 * The API contract, mirrored from the backend DTOs.
 *
 * <p>Instants are ISO-8601 strings in UTC; the `local*` fields are the same
 * moment already rendered in the business timezone. The UI reads the local
 * fields, so no page has to do timezone maths of its own.
 */

export type BusinessRole = 'BUSINESS_OWNER' | 'STAFF';

export interface Membership {
  businessId: number;
  businessName: string;
  businessSlug: string;
  role: BusinessRole;
}

export interface CurrentUser {
  id: number;
  email: string;
  fullName: string;
  activeBusiness: Membership | null;
  businesses: Membership[];
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: CurrentUser;
}

export interface Business {
  id: number;
  name: string;
  slug: string;
  description?: string | null;
  logoUrl?: string | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  timezone: string;
  active: boolean;
  createdAt: string;
}

export interface BusinessSettings {
  minimumBookingNoticeMinutes: number;
  maximumBookingAdvanceDays: number;
  slotIntervalMinutes: number;
  allowCustomerCancellation: boolean;
  cancellationDeadlineHours: number;
  defaultAppointmentDurationMinutes?: number | null;
  emailNotificationsEnabled: boolean;
  reminderNotificationsEnabled: boolean;
  reminderLeadHours: number;
}

export interface Service {
  id: number;
  name: string;
  description?: string | null;
  durationMinutes: number;
  price?: number | null;
  currency: string;
  bufferBeforeMinutes: number;
  bufferAfterMinutes: number;
  requiresStaff: boolean;
  requiresResource: boolean;
  active: boolean;
}

/** A recurring wall-clock interval; `dayOfWeek` is 1 = Monday … 7 = Sunday. */
export interface DayInterval {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

export interface Staff {
  id: number;
  name: string;
  email?: string | null;
  phone?: string | null;
  photoUrl?: string | null;
  active: boolean;
  hasLoginAccount: boolean;
  serviceIds: number[];
  workingHours: DayInterval[];
}

export interface Resource {
  id: number;
  name: string;
  description?: string | null;
  active: boolean;
  serviceIds: number[];
}

export interface Customer {
  id: number;
  name: string;
  phone?: string | null;
  email?: string | null;
  notes?: string | null;
  createdAt: string;
}

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';

export interface Booking {
  id: number;
  serviceId: number;
  serviceName: string | null;
  staffId: number | null;
  staffName: string | null;
  resourceId: number | null;
  resourceName: string | null;
  customerId: number;
  customerName: string | null;
  customerPhone: string | null;
  customerEmail: string | null;
  startDateTime: string;
  endDateTime: string;
  localStartDateTime: string;
  localEndDateTime: string;
  timezone: string;
  status: BookingStatus;
  source: 'PUBLIC' | 'DASHBOARD';
  customerNotes?: string | null;
  internalNotes?: string | null;
  price?: number | null;
  currency?: string | null;
  publicReference: string;
  createdAt: string;
}

export interface AvailableSlot {
  time: string;
  startDateTime: string;
  endDateTime: string;
  availableStaffIds: number[];
  availableResourceIds: number[];
}

export interface DayAvailability {
  date: string;
  slots: AvailableSlot[];
}

export interface BlockedTime {
  id: number;
  staffId: number | null;
  staffName: string | null;
  resourceId: number | null;
  resourceName: string | null;
  startDateTime: string;
  endDateTime: string;
  localStartDateTime: string;
  localEndDateTime: string;
  reason?: string | null;
}

export interface DashboardStats {
  todayBookings: number;
  upcomingBookings: number;
  completedBookings: number;
  cancelledBookings: number;
  todayRevenue: number | null;
  currency: string | null;
  todaySchedule: Booking[];
}

export interface CustomerHistory {
  customerId: number;
  customerName: string;
  totalAppointments: number;
  completedAppointments: number;
  cancelledAppointments: number;
  noShowAppointments: number;
  upcomingAppointments: number;
  lastAppointmentAt: string | null;
  nextAppointmentAt: string | null;
  totalSpent: number;
  averageBookingValue: number;
  favouriteServiceId: number | null;
  favouriteServiceName: string | null;
  bookings: Booking[];
}

/** The reports overview: always-on revenue totals plus rates for the selected range. */
export interface AnalyticsOverview {
  revenueToday: number;
  revenueThisWeek: number;
  revenueThisMonth: number;
  revenueThisYear: number;
  currency: string | null;
  rangeFrom: string;
  rangeTo: string;
  appointmentCount: number;
  completedCount: number;
  cancelledCount: number;
  noShowCount: number;
  averageBookingValue: number;
  cancellationRate: number;
  noShowRate: number;
}

/** One row of the "what makes the most money" report. */
export interface ServiceRevenue {
  serviceId: number;
  serviceName: string;
  active: boolean;
  appointmentCount: number;
  revenue: number;
  currency: string | null;
}

/** One row of the customer analytics table, ranked by total spend. */
export interface CustomerAnalyticsRow {
  customerId: number;
  customerName: string | null;
  appointmentCount: number;
  totalSpent: number;
  averageAppointmentValue: number;
  lastAppointmentAt: string | null;
  favouriteServiceId: number | null;
  favouriteServiceName: string | null;
  cancellationCount: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// --- public booking --------------------------------------------------------

export interface PublicBusiness {
  name: string;
  slug: string;
  description?: string | null;
  logoUrl?: string | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  timezone: string;
}

export interface PublicService {
  id: number;
  name: string;
  description?: string | null;
  durationMinutes: number;
  price?: number | null;
  currency: string;
  requiresStaff: boolean;
}

export interface PublicStaff {
  id: number;
  name: string;
  photoUrl?: string | null;
}

export interface PublicBooking {
  reference: string;
  businessName: string;
  businessSlug: string;
  businessAddress?: string | null;
  businessPhone?: string | null;
  serviceName: string;
  staffName?: string | null;
  startDateTime: string;
  endDateTime: string;
  localStartDateTime: string;
  localEndDateTime: string;
  timezone: string;
  status: BookingStatus;
  price?: number | null;
  currency?: string | null;
  customerName: string;
  cancellable: boolean;
}

/** The single error shape every endpoint returns. */
export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}
