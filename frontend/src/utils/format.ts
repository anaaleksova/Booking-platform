import dayjs from 'dayjs';
import type { Booking, BookingStatus, DayInterval } from '@/types/api';

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

export function formatMoney(amount?: number | null, currency?: string | null): string {
  if (amount === null || amount === undefined) {
    return '—';
  }
  const formatted = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(amount);
  return currency ? `${formatted} ${currency}` : formatted;
}

export function formatDuration(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

/** "Thu, 20 Aug 2026" from an ISO local date-time. */
export function formatLocalDate(localDateTime: string): string {
  return dayjs(localDateTime).format('ddd, D MMM YYYY');
}

export function formatLocalTime(localDateTime: string): string {
  return dayjs(localDateTime).format('HH:mm');
}

export function formatBookingWindow(booking: Pick<Booking, 'localStartDateTime' | 'localEndDateTime'>): string {
  return `${formatLocalTime(booking.localStartDateTime)} – ${formatLocalTime(booking.localEndDateTime)}`;
}

export function formatInterval(interval: DayInterval): string {
  return `${interval.startTime.slice(0, 5)} – ${interval.endTime.slice(0, 5)}`;
}

export const STATUS_LABELS: Record<BookingStatus, string> = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  COMPLETED: 'Completed',
  NO_SHOW: 'No-show',
};

export const STATUS_COLORS: Record<BookingStatus, 'default' | 'primary' | 'success' | 'error' | 'warning'> = {
  PENDING: 'warning',
  CONFIRMED: 'primary',
  CANCELLED: 'error',
  COMPLETED: 'success',
  NO_SHOW: 'default',
};

/** The API's `LocalDateTime` wire format, which the backend parses back. */
export function toLocalDateTimeString(date: string, time: string): string {
  return `${date}T${time.length === 5 ? `${time}:00` : time}`;
}

/** "3 days ago" from a raw (UTC) instant — computed at render time so a cached response never goes stale. */
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
  return new Intl.NumberFormat(undefined, { style: 'percent', maximumFractionDigits: 1 }).format(ratio);
}
