/**
 * The API contract, mirrored from the backend DTOs.
 *
 * <p>Instants are ISO-8601 strings in UTC; the `local*` fields are the same
 * moment already rendered in the business timezone. The UI reads the local
 * fields, so no page does timezone maths of its own.
 */
import type { SiteTheme } from '@/features/site/types';

// --- auth ------------------------------------------------------------------

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
  /** Runs the platform: the platform area, and any business's admin. */
  platformAdmin?: boolean;
  /** The active business was entered as platform administrator (not a membership). */
  platformView?: boolean;
}

// --- platform administration ---------------------------------------------------

export interface PlatformOverview {
  businesses: number;
  activeBusinesses: number;
  accounts: number;
  reservationsThisMonth: number;
  newBusinesses: number;
}

export interface PlatformBusiness {
  id: number;
  name: string;
  slug: string;
  active: boolean;
  createdAt: string;
  ownerEmails: string[];
  /** Reservations made in the last 30 days. */
  recentReservations: number;
}

export interface PlatformAccount {
  id: number;
  email: string;
  fullName: string;
  active: boolean;
  createdAt: string;
  /** e.g. "Owner · Harbor Bistro". */
  roles: string[];
  /** How many businesses know this email as a customer. */
  customerAt: number;
  platformAdmin: boolean;
}

export type PlatformAction =
  | 'ENTERED_BUSINESS'
  | 'BUSINESS_DEACTIVATED'
  | 'BUSINESS_REACTIVATED'
  | 'ACCOUNT_DEACTIVATED'
  | 'ACCOUNT_REACTIVATED';

export interface PlatformActivity {
  id: number;
  actorEmail: string;
  action: PlatformAction;
  targetType: 'BUSINESS' | 'ACCOUNT';
  targetId: number;
  targetName: string;
  createdAt: string;
}

/** A business sign-up's code was sent; `existingAccount`: the email already has a (booking) account. */
export interface SignupStarted {
  existingAccount: boolean;
}

/** A new session. The token itself is only ever in the httpOnly session cookie. */
export interface AuthResponse {
  expiresAt: string;
  user: CurrentUser;
}

// --- business ----------------------------------------------------------------

export interface Business {
  id: number;
  name: string;
  slug: string;
  description?: string | null;
  tagline?: string | null;
  accentColor: string;
  logoMediaId?: string | null;
  logoUrl?: string | null;
  coverMediaId?: string | null;
  coverUrl?: string | null;
  instagramUrl?: string | null;
  facebookUrl?: string | null;
  websiteUrl?: string | null;
  showMap: boolean;
  showGallery: boolean;
  darkTheme: boolean;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  currency: string;
  timezone: string;
  active: boolean;
  createdAt: string;
  /** Null for businesses created before sign-up asked for it. */
  businessType?: BusinessType | null;
}

/** What kind of business an owner signed up as; picks the starting setup. */
export type BusinessType = 'RESTAURANT' | 'HAIR_SALON' | 'NAILS_BEAUTY' | 'SPA' | 'FITNESS' | 'COWORKING' | 'OTHER';

export type OnboardingStepKey = 'SERVICES' | 'RESOURCES' | 'HOURS' | 'CONTACT' | 'PHOTO' | 'SHARE';

/** The owner's "Getting started" checklist. */
export interface Onboarding {
  businessType?: BusinessType | null;
  slug: string;
  steps: { key: OnboardingStepKey; done: boolean }[];
  dismissed: boolean;
}

export interface BusinessSettings {
  minimumBookingNoticeMinutes: number;
  maximumBookingAdvanceDays: number;
  slotIntervalMinutes: number;
  allowCustomerCancellation: boolean;
  cancellationDeadlineHours: number;
  emailNotificationsEnabled: boolean;
  reminderNotificationsEnabled: boolean;
  reminderLeadHours: number;
  /** Where new and cancelled reservations are announced; null for the owner's login email. */
  notificationEmail?: string | null;
}

/** A recurring wall-clock interval; `dayOfWeek` is 1 = Monday … 7 = Sunday. */
export interface DayInterval {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

export interface BlockedTime {
  id: number;
  resourceId?: number | null;
  resourceName?: string | null;
  startTime: string;
  endTime: string;
  localStartTime: string;
  localEndTime: string;
  reason?: string | null;
  /** The employee entered this time off themselves. */
  enteredByStaff: boolean;
  /** Bookings still inside the blocked time: they need moving or cancelling. */
  conflicts: ScheduleConflict[];
}

/** A booking a schedule change leaves stranded: in time off, a closure, or outside a date's special hours. */
export interface ScheduleConflict {
  reservationId: number;
  customerName?: string | null;
  serviceName?: string | null;
  participants: number;
  startTime: string;
  endTime: string;
  localStartTime: string;
  localEndTime: string;
  resources: string[];
}

/** A wall-clock span, "HH:mm". */
export interface TimeSpan {
  startTime: string;
  endTime: string;
}

/** A date whose opening hours replace the week's; no hours means closed. */
export interface SpecialHours {
  date: string;
  closed: boolean;
  hours: TimeSpan[];
  note?: string | null;
}

/** A special date in the admin panel, with the bookings it leaves outside its hours. */
export interface SpecialDay extends SpecialHours {
  conflicts: ScheduleConflict[];
}

/** An inactive resource that still has upcoming bookings. */
export interface StrandedBookings {
  resourceId: number;
  reservations: number;
}

/** Bookings moved off a deactivated resource, and those nothing else was free for. */
export interface ReassignmentResult {
  reassigned: ScheduleConflict[];
  failed: Array<{ booking: ScheduleConflict; reason: string }>;
}

export interface BulkResourceResult {
  created: Resource[];
  /** Names a resource of the type already had. */
  skipped: string[];
}

/** The signed-in staff member's own employee record. */
export interface OwnCalendar {
  employeeId: number;
  resourceId: number;
  name: string;
}

// --- catalogue ---------------------------------------------------------------

export interface ResourceType {
  id: number;
  name: string;
  description?: string | null;
  employeeType: boolean;
  active: boolean;
  resourceCount: number;
  /** How many floor plans (floors or areas) the business has drawn for this type. */
  floorPlanCount: number;
}

export type SpotShape = 'ROUND' | 'SQUARE' | 'RECT';

/** One resource on a floor plan. All values are percentages of the plan; x and y are the centre. */
export interface FloorPlanSpot {
  resourceId: number;
  x: number;
  y: number;
  width: number;
  height: number;
  shape: SpotShape;
}

export type DecorKind = 'ZONE' | 'WALL' | 'COUNTER' | 'PLANT' | 'ENTRANCE';
export type DecorTone = 'GREEN' | 'PINK' | 'BLUE' | 'SAND';

/** A non-bookable part of the room (area, wall, bar, plant, entrance); same units as a spot. */
export interface FloorPlanDecor {
  kind: DecorKind;
  label?: string | null;
  /** Floor tint; areas only. */
  tone?: DecorTone | null;
  x: number;
  y: number;
  width: number;
  height: number;
}

/** One floor (or area) of a resource type's floor plans. */
export interface FloorPlan {
  id: number;
  resourceTypeId: number;
  name: string;
  /** Width divided by height. */
  aspectRatio: number;
  items: FloorPlanSpot[];
  decor: FloorPlanDecor[];
}

/** One floor as saved; no id for a new floor. Floors are saved all together, in order. */
export interface FloorPlanInput {
  id?: number;
  name: string;
  aspectRatio: number;
  items: FloorPlanSpot[];
  decor: FloorPlanDecor[];
}

export interface Resource {
  id: number;
  resourceTypeId: number;
  resourceTypeName?: string | null;
  name: string;
  description?: string | null;
  /** Participants served at once; null = not limited by participants. */
  capacity: number | null;
  /** Several groups can book it at once until its seats are taken. */
  sharedSeating: boolean;
  /** Smallest group it is given to when not shared; null = any group. */
  minPartySize: number | null;
  active: boolean;
  imageMediaId?: string | null;
  imageUrl?: string | null;
  employeeManaged: boolean;
  serviceIds: number[];
  hours: DayInterval[];
}

export interface Employee {
  id: number;
  resourceId: number;
  name: string;
  email?: string | null;
  phone?: string | null;
  imageMediaId?: string | null;
  imageUrl?: string | null;
  active: boolean;
  capacity: number | null;
  hasLoginAccount: boolean;
  serviceIds: number[];
  workingHours: DayInterval[];
}

export interface Requirement {
  id: number;
  resourceTypeId: number;
  resourceTypeName?: string | null;
  quantity: number;
  required: boolean;
  customerCanChoose: boolean;
  /** Quantity is a minimum: resources are combined for groups one cannot seat. */
  combineResources: boolean;
}

export interface Service {
  id: number;
  name: string;
  description?: string | null;
  category?: string | null;
  imageMediaId?: string | null;
  imageUrl?: string | null;
  durationMinutes: number;
  price: number;
  pricePerParticipant: boolean;
  maxParticipants: number;
  bufferBeforeMinutes: number;
  bufferAfterMinutes: number;
  allowCustomerResourceSelection: boolean;
  active: boolean;
  /** Online bookings are requests the owner approves. */
  approvalRequired: boolean;
  /** With approval on, only groups of at least this size need it; null = every booking. */
  approvalFromParticipants?: number | null;
  requirements: Requirement[];
}

export interface ServiceInput {
  name: string;
  description?: string;
  category?: string;
  imageMediaId?: string | null;
  durationMinutes: number;
  price: number;
  pricePerParticipant: boolean;
  maxParticipants: number;
  bufferBeforeMinutes: number;
  bufferAfterMinutes: number;
  allowCustomerResourceSelection: boolean;
  active: boolean;
  approvalRequired: boolean;
  approvalFromParticipants?: number | null;
  requirements: Array<Pick<Requirement, 'resourceTypeId' | 'quantity' | 'required' | 'customerCanChoose' | 'combineResources'>>;
}

export interface ServiceDeletion {
  deleted: boolean;
  deactivated: boolean;
  message: string;
}

// --- customers ---------------------------------------------------------------

export interface Customer {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  email?: string | null;
  phone?: string | null;
  notes?: string | null;
  reservationCount: number;
  totalSpent: number;
  lastReservationAt?: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// --- availability & reservations -------------------------------------------

export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';

export interface ResourceOption {
  id: number;
  name: string;
  capacity: number | null;
  /** For a shared resource, the seats still free at this time. */
  seatsLeft?: number | null;
}

export interface SlotRequirement {
  requirementId: number;
  resourceTypeId: number;
  resourceTypeName: string;
  quantity: number;
  required: boolean;
  customerChoosable: boolean;
  /** Resources are combined for larger groups; quantity is then a minimum. */
  combinable: boolean;
  /** Free resources, best fit first. Customer-facing: only for choosable requirements. */
  resources: ResourceOption[];
  /** For a combinable requirement: the best ways to seat the group at this time, best first. */
  allocations: AllocationOption[];
}

/** One way to seat the group — one resource or several, found by the server. */
export interface AllocationOption {
  resources: ResourceOption[];
  /** Combined capacity at this time; null when not limited by participants. */
  capacity: number | null;
  /** The floor plan the resources stand on, if any. */
  area: string | null;
}

export interface Slot {
  /** Business-local wall clock, "HH:mm:ss". */
  time: string;
  startTime: string;
  endTime: string;
  requirements: SlotRequirement[];
}

export interface DaySlots {
  date: string;
  slots: Slot[];
}

export interface ResourceSelection {
  requirementId: number;
  resourceIds: number[];
}

export interface AssignedResource {
  resourceId: number;
  name: string;
  resourceTypeId?: number | null;
  resourceTypeName?: string | null;
  requirementId?: number | null;
}

export interface Reservation {
  id: number;
  reference: string;
  serviceId: number;
  serviceName: string;
  customerId: number;
  customerName: string;
  customerEmail?: string | null;
  customerPhone?: string | null;
  numberOfParticipants: number;
  startTime: string;
  endTime: string;
  localStartTime: string;
  localEndTime: string;
  timezone: string;
  status: ReservationStatus;
  source: ReservationSource;
  originalPrice: number;
  discountAmount: number;
  finalPrice: number;
  currency: string;
  discountCode?: string | null;
  resources: AssignedResource[];
  customerNotes?: string | null;
  internalNotes?: string | null;
  createdAt: string;
  cancelledAt?: string | null;
  cancellationReason?: string | null;
}

export interface CustomerHistory {
  customerId: number;
  customerName: string;
  totalReservations: number;
  completedReservations: number;
  cancelledReservations: number;
  noShowReservations: number;
  upcomingReservations: number;
  lastVisitAt?: string | null;
  nextReservationAt?: string | null;
  totalSpent: number;
  averageReservationValue: number;
  favouriteServiceName?: string | null;
  /** One page of the history, newest first; the totals above cover all of it. */
  reservations: Page<Reservation>;
}

export interface PriceQuote {
  originalPrice: number;
  discountAmount: number;
  finalPrice: number;
  currency: string;
  discountCode?: string | null;
  discountName?: string | null;
}

// --- discounts ---------------------------------------------------------------

export type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT';
export type DiscountState = 'ACTIVE' | 'SCHEDULED' | 'EXPIRED' | 'EXHAUSTED' | 'INACTIVE';

export interface Discount {
  id: number;
  name: string;
  code: string;
  type: DiscountType;
  value: number;
  startsOn?: string | null;
  endsOn?: string | null;
  active: boolean;
  maxUsage?: number | null;
  usageCount: number;
  minBookingValue?: number | null;
  appliesToAllServices: boolean;
  serviceIds: number[];
  state: DiscountState;
}

export type DiscountInput = Omit<Discount, 'id' | 'usageCount' | 'state'>;

// --- events --------------------------------------------------------------------

/** UPCOMING: not on the booking page yet; SHOWING: on it now; PAST: no longer shown. */
export type EventState = 'UPCOMING' | 'SHOWING' | 'PAST';

/** Dates are business-local "YYYY-MM-DD", times business-local "HH:mm[:ss]". */
export interface BusinessEvent {
  id: number;
  title: string;
  description?: string | null;
  performer?: string | null;
  imageMediaId?: string | null;
  imageUrl?: string | null;
  date: string;
  startTime: string;
  /** Earlier than the start when the event runs past midnight. */
  endTime?: string | null;
  showFrom: string;
  showUntil: string;
  serviceId?: number | null;
  state: EventState;
}

/** Empty show dates take the defaults: a week before, until the day itself. */
export type EventInput = Omit<BusinessEvent, 'id' | 'imageUrl' | 'state' | 'showFrom' | 'showUntil'> & {
  showFrom: string | null;
  showUntil: string | null;
};

/** OPEN: tables left; FULL: fully booked; NONE: nothing to book (no service, or already started). */
export type EventBooking = 'OPEN' | 'FULL' | 'NONE';

export interface PublicEvent {
  id: number;
  title: string;
  description?: string | null;
  performer?: string | null;
  imageUrl?: string | null;
  date: string;
  startTime: string;
  endTime?: string | null;
  serviceId?: number | null;
  booking: EventBooking;
}

// --- analytics ---------------------------------------------------------------

export interface RevenueSummary {
  from: string;
  to: string;
  reservations: number;
  grossRevenue: number;
  discounts: number;
  netRevenue: number;
  averageReservationValue: number;
  currency: string;
}

export interface RevenuePeriods {
  today: RevenueSummary;
  thisWeek: RevenueSummary;
  thisMonth: RevenueSummary;
}

export type Granularity = 'DAY' | 'WEEK' | 'MONTH';

export interface RevenuePoint {
  periodStart: string;
  reservations: number;
  grossRevenue: number;
  discounts: number;
  netRevenue: number;
}

export interface RevenueReport {
  summary: RevenueSummary;
  granularity: Granularity;
  series: RevenuePoint[];
}

export interface ReservationPoint {
  date: string;
  total: number;
  pending: number;
  confirmed: number;
  completed: number;
  cancelled: number;
  noShows: number;
  participants: number;
}

export interface ReservationReport {
  from: string;
  to: string;
  total: number;
  cancelled: number;
  noShows: number;
  /** No-shows / (completed + no-shows), 0..1; null when neither happened in the range. */
  noShowRate: number | null;
  series: ReservationPoint[];
}

/** The channel a reservation came through. */
export type ReservationSource = 'WEB' | 'ADMIN' | 'INSTAGRAM' | 'FACEBOOK' | 'API';

export interface SourceStat {
  source: ReservationSource;
  reservations: number;
  /** Of all non-cancelled reservations in the range, 0..1. */
  share: number;
  revenue: number;
  revenueShare: number;
}

export interface ServiceStat {
  serviceId: number;
  serviceName: string;
  active: boolean;
  reservations: number;
  participants: number;
  revenue: number;
}

export interface ResourceUtilization {
  resourceId: number;
  resourceName: string;
  resourceTypeId: number;
  resourceTypeName?: string | null;
  reservations: number;
  bookedMinutes: number;
  availableMinutes: number;
  utilization: number;
}

export interface CustomerReport {
  from: string;
  to: string;
  activeCustomers: number;
  newCustomers: number;
  returningCustomers: number;
  topCustomers: Array<{ customerId: number; name: string; reservations: number; spent: number }>;
}

export interface Dashboard {
  currency: string;
  todayRevenue: number;
  todayReservations: number;
  monthRevenue: number;
  monthReservations: number;
  totalCustomers: number;
  upcoming: Reservation[];
}

// --- customer-facing ---------------------------------------------------------

export interface PublicBusiness {
  name: string;
  slug: string;
  tagline?: string | null;
  description?: string | null;
  accentColor: string;
  logoUrl?: string | null;
  coverUrl?: string | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  instagramUrl?: string | null;
  facebookUrl?: string | null;
  websiteUrl?: string | null;
  showMap: boolean;
  darkTheme: boolean;
  timezone: string;
  currency: string;
  openingHours: DayInterval[];
  /** Dates in the coming weeks whose hours differ from the week's. */
  specialHours: SpecialHours[];
  /** Empty when the business has no photos or hides its gallery. */
  gallery: Array<{ url: string; caption?: string | null }>;
  /** The published homepage theme; every customer page is drawn in it. */
  theme?: SiteTheme | null;
}

export interface GalleryImage {
  id: number;
  mediaId: string;
  url: string;
  caption?: string | null;
  position: number;
}

export interface ChoosableOption {
  id: number;
  name: string;
  description?: string | null;
  imageUrl?: string | null;
  capacity: number | null;
  /** Other groups may be seated at it too, until its seats are taken. */
  shared: boolean;
  /** Smallest group it is given to (private resources only). */
  minPartySize?: number | null;
}

export interface ChoosableRequirement {
  requirementId: number;
  resourceTypeName: string;
  quantity: number;
  /** Resources are combined for larger groups: whole combinations are offered per time. */
  combinable: boolean;
  resources: ChoosableOption[];
  /** Maps of the place, one per floor, in order; empty when the business has not drawn any. */
  floorPlans: PublicFloorPlan[];
}

export interface PublicFloorPlan {
  id: number;
  name: string;
  aspectRatio: number;
  spots: FloorPlanSpot[];
  decor: FloorPlanDecor[];
}

export interface PublicService {
  id: number;
  name: string;
  description?: string | null;
  category?: string | null;
  imageUrl?: string | null;
  durationMinutes: number;
  price: number;
  pricePerParticipant: boolean;
  maxParticipants: number;
  currency: string;
  /** Online bookings are requests the business confirms. */
  approvalRequired: boolean;
  /** With approval on, the group size from which it applies; null = always. */
  approvalFromParticipants?: number | null;
  choosableRequirements: ChoosableRequirement[];
}

export interface UploadedMedia {
  id: string;
  url: string;
  contentType: string;
  sizeBytes: number;
}

export interface PublicReservation {
  reference: string;
  businessName: string;
  businessSlug: string;
  businessAddress?: string | null;
  businessPhone?: string | null;
  serviceName: string;
  numberOfParticipants: number;
  startTime: string;
  endTime: string;
  localStartTime: string;
  localEndTime: string;
  timezone: string;
  status: ReservationStatus;
  originalPrice: number;
  discountAmount: number;
  finalPrice: number;
  currency: string;
  discountCode?: string | null;
  customerName?: string | null;
  resources: Array<{ resourceTypeName?: string | null; name: string }>;
  cancellable: boolean;
}

/** The single error shape every endpoint returns. */
export interface ApiError {
  timestamp: string;
  status: number;
  /** Stable machine-readable code, e.g. RESOURCE_NOT_AVAILABLE. */
  error: string;
  message: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}

// --- integrations ----------------------------------------------------------

export type IntegrationStatus = 'NOT_CONNECTED' | 'CONNECTED' | 'DISABLED';

export type IntegrationPermission =
  | 'READ_BUSINESS'
  | 'READ_SERVICES'
  | 'READ_AVAILABILITY'
  | 'CREATE_RESERVATION'
  | 'READ_RESERVATION'
  | 'CANCEL_RESERVATION'
  | 'READ_CUSTOMER'
  | 'CREATE_CUSTOMER'
  | 'MANAGE_CONVERSATIONS';

export interface N8nConnection {
  status: IntegrationStatus;
  webhookUrl?: string | null;
  signingSecret?: string | null;
  updatedAt?: string | null;
}

export interface ChannelConnection {
  provider: 'INSTAGRAM' | 'FACEBOOK';
  status: IntegrationStatus;
  externalAccountId?: string | null;
  displayName?: string | null;
  oauthAvailable: boolean;
}

export interface IntegrationToken {
  id: number;
  name: string;
  prefix: string;
  permissions: IntegrationPermission[];
  createdAt: string;
  lastUsedAt?: string | null;
  revokedAt?: string | null;
}

export interface IntegrationEventDelivery {
  id: number;
  eventType: string;
  reservationId?: number | null;
  deliveryId: string;
  status: 'PENDING' | 'DELIVERED' | 'FAILED' | 'SKIPPED';
  attempts: number;
  responseStatus?: number | null;
  lastError?: string | null;
  dueAt: string;
  deliveredAt?: string | null;
  createdAt: string;
}

/** The Facebook Page (and its Instagram) connected by signing in with Meta. */
export interface MetaConnection {
  /** The platform's Meta app is set up, so the Connect button works. */
  available: boolean;
  connected: boolean;
  pageId?: string | null;
  pageName?: string | null;
  instagramAccountId?: string | null;
  instagramUsername?: string | null;
  connectedAt?: string | null;
}

export interface MetaPageChoice {
  id: string;
  name: string;
  instagramUsername?: string | null;
  /** Already connected to another business; it cannot be picked. */
  connectedElsewhere: boolean;
}

export interface MetaConnectResult {
  needsPageChoice: boolean;
  connection?: MetaConnection | null;
  sessionId?: string | null;
  pages?: MetaPageChoice[] | null;
}

export interface IntegrationsOverview {
  businessId: number;
  apiBaseUrl: string;
  n8n: N8nConnection;
  meta: MetaConnection;
  channels: ChannelConnection[];
  tokens: IntegrationToken[];
  recentEvents: IntegrationEventDelivery[];
  availablePermissions: IntegrationPermission[];
}

export interface CreatedIntegrationToken {
  token: IntegrationToken;
  /** Shown once; only a hash is stored. */
  secret: string;
}

export interface WebhookTestResult {
  delivered: boolean;
  httpStatus?: number | null;
  error?: string | null;
}
