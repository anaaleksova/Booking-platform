import { describe, expect, it } from 'vitest';
import { dayName, formatCapacity, formatDuration, formatInterval, formatMoney, pluralize } from '@/utils/format';

describe('format', () => {
  it('names ISO weekdays', () => {
    expect(dayName(1)).toBe('Monday');
    expect(dayName(7)).toBe('Sunday');
    expect(dayName(8)).toBe('');
  });

  it('formats durations in minutes and hours', () => {
    expect(formatDuration(45)).toBe('45 min');
    expect(formatDuration(60)).toBe('1 h');
    expect(formatDuration(90)).toBe('1 h 30 min');
  });

  it('shows a dash for a missing amount', () => {
    expect(formatMoney(null, 'EUR')).toBe('—');
    expect(formatMoney(12.5, 'EUR')).toContain('12.50');
  });

  it('treats no capacity as unlimited', () => {
    expect(formatCapacity(null)).toBe('Unlimited');
    expect(formatCapacity(4)).toBe('4');
  });

  it('trims seconds off opening hours', () => {
    expect(formatInterval({ dayOfWeek: 1, startTime: '09:00:00', endTime: '17:30:00' } as never)).toBe('09:00 – 17:30');
  });

  it('pluralizes', () => {
    expect(pluralize(1, 'guest')).toBe('1 guest');
    expect(pluralize(3, 'guest')).toBe('3 guests');
    expect(pluralize(2, 'person', 'people')).toBe('2 people');
  });
});
