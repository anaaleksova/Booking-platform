import dayjs from 'dayjs';
import type { DayInterval, DiscountState, ReservationSource, ReservationStatus } from '@/types/api';

/**
 * Formatting helpers.
 *
 * <p>All display goes through the `local*` fields the API already rendered in
 * the business timezone, so the browser's own zone is never involved.
 */

export const DAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

export function dayName(dayOfWeek: number): string {
  return DAY_NAMES[dayOfWeek - 1] ?? '';
}

/** "€50", "€1,250.50" — the currency symbol comes from the business currency. */
export function formatMoney(amount?: number | null, currency?: string | null): string {
  if (amount === null || amount === undefined) {
    return '—';
  }
  if (!currency) {
    return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(amount);
  }
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    minimumFractionDigits: Number.isInteger(amount) ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

/** Compact money for axes and KPI cards: €12.9K. */
export function formatMoneyCompact(amount: number, currency: string): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    notation: amount >= 10_000 ? 'compact' : 'standard',
    maximumFractionDigits: amount >= 10_000 ? 1 : 0,
  }).format(amount);
}

export function formatDuration(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

/** "Thu, 20 Aug 2026" from an ISO local date or date-time. */
export function formatLocalDate(local: string): string {
  return dayjs(local).format('ddd, D MMM YYYY');
}

export function formatShortDate(local: string): string {
  return dayjs(local).format('D MMM');
}

export function formatLocalTime(localDateTime: string): string {
  return dayjs(localDateTime).format('HH:mm');
}

export function formatWindow(item: { localStartTime: string; localEndTime: string }): string {
  return `${formatLocalTime(item.localStartTime)} – ${formatLocalTime(item.localEndTime)}`;
}

export function formatInterval(interval: DayInterval): string {
  return `${interval.startTime.slice(0, 5)} – ${interval.endTime.slice(0, 5)}`;
}

export function formatCapacity(capacity: number | null | undefined): string {
  return capacity === null || capacity === undefined ? 'Unlimited' : String(capacity);
}

export function pluralize(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}

export const STATUS_LABELS: Record<ReservationStatus, string> = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  COMPLETED: 'Completed',
};

export const STATUS_COLORS: Record<ReservationStatus, 'default' | 'primary' | 'success' | 'error' | 'warning'> = {
  PENDING: 'warning',
  CONFIRMED: 'primary',
  CANCELLED: 'error',
  COMPLETED: 'success',
};

export const SOURCE_LABELS: Record<ReservationSource, string> = {
  WEB: 'Website',
  ADMIN: 'Admin',
  INSTAGRAM: 'Instagram',
  FACEBOOK: 'Facebook',
  API: 'API',
};

/** How a reservation was made, as a phrase: "Booked · online by the customer". */
export const SOURCE_DESCRIPTIONS: Record<ReservationSource, string> = {
  WEB: 'online by the customer',
  ADMIN: 'by the business',
  INSTAGRAM: 'by the customer via Instagram',
  FACEBOOK: 'by the customer via Facebook',
  API: 'through the API',
};

export const DISCOUNT_STATE_LABELS: Record<DiscountState, string> = {
  ACTIVE: 'Active',
  SCHEDULED: 'Scheduled',
  EXPIRED: 'Expired',
  EXHAUSTED: 'Used up',
  INACTIVE: 'Inactive',
};

/** The API's `LocalDateTime` wire format, which the backend parses back. */
export function toLocalDateTimeString(date: string, time: string): string {
  return `${date}T${time.length === 5 ? `${time}:00` : time}`;
}

export function formatDaysAgo(instant?: string | null): string {
  if (!instant) {
    return 'Never';
  }
  const days = dayjs().startOf('day').diff(dayjs(instant).startOf('day'), 'day');
  if (days <= 0) {
    return 'Today';
  }
  if (days === 1) {
    return 'Yesterday';
  }
  return `${days} days ago`;
}

export function formatPercent(ratio: number): string {
  return new Intl.NumberFormat(undefined, { style: 'percent', maximumFractionDigits: 0 }).format(ratio);
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat().format(value);
}
